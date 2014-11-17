package models;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonView;

import play.Logger;
import utils.DataStore;


/**
 * This represents where a feed comes from/came from.
 * @author mattwigway
 *
 */
@JsonInclude(Include.ALWAYS)
public class FeedSource extends Model {
    private static DataStore<FeedSource> sourceStore = new DataStore<FeedSource>("feedsources");
    
    /**
     * The collection of which this feed is a part
     */
    @JsonView(JsonViews.DataDump.class)
    public String feedCollectionId;
    
    /**
     * Get the FeedCollection of which this feed is a part
     */
    @JsonView(JsonViews.UserInterface.class)
    public FeedCollection getFeedCollection () {
        return FeedCollection.get(feedCollectionId);
    }
    
    public void setFeedCollection(FeedCollection c) {
        this.feedCollectionId = c.id;
    }
    
    /** The name of this feed source, e.g. MTA New York City Subway */
    public String name;
    
    /** Is this feed public, i.e. should it be placed in deployments and listed on the
     * public feeds page for download?
     */
    public boolean isPublic;
    
    /**
     * How do we receive this feed?
     */
    public FeedRetrievalMethod retrievalMethod;
    
    /**
     * When was this feed last fetched?
     */
    public Date lastFetched;
    
    /**
     * When was this feed last updated?
     */
    //public transient Date lastUpdated;
    
    /**
     * From whence is this feed fetched?
     */
    public URL url;
    
    /**
     * What is the GTFS Editor ID of this feed?
     */
    public String editorId;
    
    /**
     * Create a new feed. This also creates a user to own this feed.
     */
    public FeedSource (String name) {
        super();
        
        this.name = name;
        
        // create a user for this feed
        String username = this.name;
        int i = 0;
        
        // feed source names are not always unique. find a name that is.
        while (true) {
            if (User.getUserByUsername(username) != null) {
                i++;
                username = this.name + "_" + i;
            }
            else {
                break;
            }
        }
        
        // create a new user to own this feed source, with no password (a login key will be generated) and no email address
        User u = new User(username, null, null);
        u.active = true;
        u.admin = false;
        u.autogenerated = true;
        u.save();
        this.userId = u.id;
    }
    
    /**
     * No-arg constructor to yield an uninitialized feed source, for dump/restore.
     * Should not be used in general code.
     */
    public FeedSource () {
        // do nothing
    }
    
    @Override
    public void setUser (User u) {
        throw new IllegalArgumentException("FeedSources are permanently associated with a single user");
    }
    
    /**
     * Fetch the latest version of the feed.
     */
    public FeedVersion fetch () {
        if (this.retrievalMethod.equals(FeedRetrievalMethod.MANUALLY_UPLOADED)) {
            Logger.info("not fetching feed {}, not a fetchable feed", this.toString());
            return null;
        }
        
        // fetchable feed, continue
        FeedVersion latest = getLatest();
        
        // We create a new FeedVersion now, so that the fetched date is (milliseconds) before
        // fetch occurs. That way, in the highly unlikely event that a feed is updated while we're
        // fetching it, we will not miss a new feed.
        FeedVersion newFeed = new FeedVersion(this);
        
        // make the request, using the proper HTTP caching headers to prevent refetch, if applicable
        HttpURLConnection conn;
        try {
            conn = (HttpURLConnection) url.openConnection();
        } catch (IOException e) {
            Logger.error("Unable to open connection to {}; not fetching feed {}", url, this);
            newFeed.dereference();
            return null;
        }
        
        conn.setDefaultUseCaches(true);
        
        if (latest != null)
            conn.setIfModifiedSince(latest.updated.getTime());
        
        try {
            conn.connect();
        
            if (conn.getResponseCode() == HttpURLConnection.HTTP_NOT_MODIFIED) {
                Logger.info("Feed {} has not been modified", this);
                newFeed.dereference();
                return null;
            }

            // TODO: redirects
            else if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                Logger.info("Saving feed {}", this);

                File out = newFeed.newFeed();
                
                FileOutputStream outStream;
                
                try {
                    outStream = new FileOutputStream(out);
                } catch (FileNotFoundException e) {
                    Logger.error("Unable to open {}", out);
                    newFeed.dereference();
                    return null;
                }
                
                // copy the file
                ReadableByteChannel rbc = Channels.newChannel(conn.getInputStream());
                outStream.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
                outStream.close();
            }
            
            else {
                Logger.error("HTTP status {} retrieving feed {}", conn.getResponseMessage(), this);
                newFeed.dereference();
                return null;
            }
        } catch (IOException e) {
            Logger.error("Unable to connect to {}; not fetching feed {}", url, this);
            newFeed.dereference();
            return null;
        }
        
        // validate the fetched file
        // note that anything other than a new feed fetched successfully will have already returned from the function
        newFeed.hash();
        
        if (latest != null && newFeed.hash.equals(latest.hash)) {
            Logger.warn("Feed {} was fetched but has not changed; server operators should add If-Modified-Since support to avoid wasting bandwidth", this);
            newFeed.getFeed().delete();
            newFeed.dereference();
            return null;
        }
        else {
            newFeed.userId = this.userId;
            newFeed.validate();
            newFeed.save();
            return newFeed;
        }
    }
    
    public String toString () {
        return "<FeedSource " + this.name + " (" + this.id + ")>";
    }
    
    public void save () {
        save(true);
    }
    
    public void save (boolean commit) {
        if (commit)
            sourceStore.save(this.id, this);
        else
            sourceStore.saveWithoutCommit(this.id, this);
    }
    
    /**
     * Get the latest version of this feed
     * @return the latest version of this feed
     */
    @JsonIgnore
    public FeedVersion getLatest () {
        FeedVersion latest = null;
    
        for (FeedVersion version : FeedVersion.getAll()) {
            // there could be feedsources in the datastore that haven't finished initializing
            if (this.id.equals(version.feedSourceId)) {
                if (latest == null || version.updated.after(latest.updated)) {
                    latest = version;
                }
            }
        }
        
        return latest;
    }
    
    @JsonInclude(Include.NON_NULL)
    @JsonView(JsonViews.UserInterface.class)
    public String getLatestVersionId () {
        FeedVersion latest = getLatest();
        return latest != null ? latest.id : null;
    }
    
    /**
     * We can't pass the entire latest feed source back, because it contains references back to this feedsource,
     * so Jackson doesn't work. So instead we specifically expose the validation results and the latest update.
     * @param id
     * @return
     */
    // TODO: use summarized feed source here. requires serious refactoring on client side.
    @JsonInclude(Include.NON_NULL)
    @JsonView(JsonViews.UserInterface.class)
    public Date getLastUpdated() {
        FeedVersion latest = getLatest();
        return latest != null ? latest.updated : null;
    }
    
    @JsonInclude(Include.NON_NULL)
    @JsonView(JsonViews.UserInterface.class)
    public FeedValidationResultSummary getLatestValidation () {
        FeedVersion latest = getLatest();
        return latest != null ? new FeedValidationResultSummary(latest.validationResult) : null;
    }
    
    public static FeedSource get(String id) {
        return sourceStore.getById(id);
    }

    public static Collection<FeedSource> getAll() {
        return sourceStore.getAll();
    }
    
    /**
     * Get all of the feed versions for this source
     * @return
     */
    @JsonIgnore
    public Collection<FeedVersion> getFeedVersions() {
        // TODO Indices
        ArrayList<FeedVersion> ret = new ArrayList<FeedVersion>();
        
        for (FeedVersion v : FeedVersion.getAll()) {
            if (this.id.equals(v.feedSourceId)) {
                ret.add(v);
            }
        }
        
        return ret;
    }
    
    /**
     * Represents ways feeds can be retrieved
     */
    public static enum FeedRetrievalMethod {
        FETCHED_AUTOMATICALLY, // automatically retrieved over HTTP on some regular basis
        MANUALLY_UPLOADED, // manually uploaded by someone, perhaps the agency, or perhaps an internal user
        PRODUCED_IN_HOUSE // produced in-house in a GTFS Editor instance
    }
    
    public static void commit() {
        sourceStore.commit();
    }

    /**
     * Delete this feed source and everything that it contains.
     */
    public void delete() {
        for (FeedVersion v : getFeedVersions()) {
            v.delete();
        }
        
        sourceStore.delete(this.id);
    }
}
