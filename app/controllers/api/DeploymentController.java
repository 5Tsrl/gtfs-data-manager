package controllers.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import jobs.DeployJob;
import models.Deployment;
import models.FeedCollection;
import models.FeedSource;
import models.FeedVersion;
import models.JsonViews;
import models.User;
import models.User.ProjectPermissions;
import play.libs.Akka;
import play.libs.Json;
import play.mvc.BodyParser;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Security;
import scala.concurrent.duration.Duration;
import utils.DeploymentManager;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;

import controllers.Secured;

@Security.Authenticated(Secured.class)
public class DeploymentController extends Controller {
    private static JsonManager<Deployment> json =
            new JsonManager<Deployment>(Deployment.class, JsonViews.UserInterface.class);
    
    private static JsonManager<DeployJob.DeployStatus> statusJson =
            new JsonManager<DeployJob.DeployStatus>(DeployJob.DeployStatus.class, JsonViews.UserInterface.class);

    private static HashMap<String, DeployJob> deploymentJobsByServer = new HashMap<String, DeployJob>();
    
    public static Result get (String id) throws JsonProcessingException {
        User currentUser = User.getUserByUsername(session("username"));
        Deployment d = Deployment.get(id);
        
        if (!currentUser.admin && !currentUser.equals(d.getUser()))
            return unauthorized();
        else
            return ok(json.write(d)).as("application/json");
    }

    public static Result getAll () throws JsonProcessingException {
        User currentUser = User.getUserByUsername(session("username"));
        if (!currentUser.admin)
            return unauthorized();
        
        String feedCollectionId = request().getQueryString("feedCollection");
        if (feedCollectionId != null) {
            FeedCollection c = FeedCollection.get(feedCollectionId);
            return ok(json.write(c.getDeployments())).as("application/json");
        }
        else {
            return ok(json.write(Deployment.getAll())).as("application/json");
        }
    }

    public static Result create () throws JsonProcessingException {
        User currentUser = User.getUserByUsername(session("username"));
        JsonNode params = request().body().asJson();

        if (!currentUser.admin)
            return unauthorized();
        
        // find the feed collection
        FeedCollection c = FeedCollection.get(params.get("feedCollection").get("id").asText());

        Deployment d = new Deployment(c);
        d.setUser(currentUser);

        applyJsonToDeployment(d, params);
        
        d.save();
        
        return ok(json.write(d)).as("application/json");
    }
    
    /**
     * Create a deployment for a particular feedsource
     * @throws JsonProcessingException 
     */
    public static Result createFromFeedSource (String feedSourceId) throws JsonProcessingException {
        User currentUser = User.getUserByUsername(session("username"));
        FeedSource s = FeedSource.get(feedSourceId);
        
        // does the user have permission to do this through project permissions?
        boolean hasPermission = false;
        
        if (currentUser.projectPermissions != null) {
            for (ProjectPermissions p : currentUser.projectPermissions) {
                if (s.id.equals(p.project_id) && p.write) {
                    hasPermission = true;
                    break;                            
                }
            }
        }
        
        // three ways to have permission to do this:
        // 1) be an admin
        // 2) be the autogenerated user associated with this feed
        // 3) have access to this feed through project permissions
        // if all fail, the user cannot do this.
        if (!currentUser.admin && !currentUser.equals(s.getUser()) && !hasPermission)
            return unauthorized();
        
        Deployment d = new Deployment(s);
        d.setUser(currentUser);
        d.save();
        
        return ok(json.write(d)).as("application/json");
    }
    
    @BodyParser.Of(value=BodyParser.Json.class, maxLength=1024*1024)
    public static Result update (String id) throws JsonProcessingException {
        User currentUser = User.getUserByUsername(session("username"));
        Deployment d = Deployment.get(id);
        
        if (!currentUser.admin && !currentUser.equals(d.getUser()))
            return unauthorized();
        
        if (d == null)
            return notFound();
        
        JsonNode params = request().body().asJson();
        applyJsonToDeployment(d, params);
        
        d.save();
        
        return ok(json.write(d)).as("application/json");
    }

    /**
     * Apply JSON params to a deployment. 
     * @param d
     * @param params
     */
    private static void applyJsonToDeployment(Deployment d, JsonNode params) {
        JsonNode versions = params.get("feedVersions");

        if (versions != null && !(versions instanceof NullNode)) {
            ArrayList<FeedVersion> versionsToInsert = new ArrayList<FeedVersion>(versions.size());
            
            for (JsonNode version : versions) {
                FeedVersion v = FeedVersion.get(version.get("id").asText());

                if (v.getFeedSource().feedCollectionId.equals(d.feedCollectionId)) {
                    versionsToInsert.add(v);
                }
            }
            
            d.setFeedVersions(versionsToInsert);
        }
        
        String name = params.get("name").asText();
        
        if (name != null) {
            d.name = name;
        }
    }
    
    /**
     * Create a deployment bundle, and push it to OTP
     * @throws IOException 
     */
    public static Result deploy (String id, String target) throws IOException {
        User currentUser = User.getUserByUsername(session("username"));
        Deployment d = Deployment.get(id);
        
        if (!currentUser.admin && !currentUser.equals(d.getUser()))
            return unauthorized();
        
        // check if we can deploy
        if (deploymentJobsByServer.containsKey(target)) {
            DeployJob currentJob = deploymentJobsByServer.get(target);
            if (currentJob != null && !currentJob.getStatus().completed) {
                // send a 503 service unavailable as it is not possible to deploy to this target right now;
                // someone else is deploying
                return status(503);
            }
        }
        
        List<String> targetUrls = DeploymentManager.getDeploymentUrls(target);
        
        if (targetUrls == null)
            return badRequest("No such server to deploy to!");
        
        Deployment oldD = Deployment.getDeploymentForServerAndRouterId(target, d.routerId);
        if (oldD != null) {
            oldD.deployedTo = null;
            oldD.save();
        }
        
        d.deployedTo = target;
        d.save();
        
        DeployJob job = new DeployJob(d, targetUrls, DeploymentManager.getPublicUrl(target));
        
        deploymentJobsByServer.put(target, job);
        
        Akka.system().scheduler().scheduleOnce(
                Duration.create(50, TimeUnit.MILLISECONDS),
                job,
                Akka.system().dispatcher()
                );
        
        return ok();
    }
    
    /**
     * The current status of a deployment, polled to update the progress dialog.
     * @throws JsonProcessingException 
     */
    public static Result deploymentStatus (String target) throws JsonProcessingException {
        // this is not access-controlled beyond requiring auth, which is fine
        // there's no good way to know who should be able to see this.
        if (!deploymentJobsByServer.containsKey(target))
            return notFound();
        
        DeployJob j = deploymentJobsByServer.get(target);
        
        if (j == null)
            return notFound();
        
        return ok(statusJson.write(j.getStatus())).as("application/json");
    }
    
    /**
     * The servers that it is possible to deploy to.
     */
    public static Result targets () {
        return ok(Json.toJson(DeploymentManager.getDeploymentNames()));
    }
}