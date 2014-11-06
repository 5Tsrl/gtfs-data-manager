var Backbone = require('backbone');
Backbone.Marionette = require('backbone.marionette');
var $ = require('jquery');
var _ = require('underscore');
var FeedCollection = require('feed-collection');
var FeedSourceCollection = require('feed-source-collection');
var FeedSourceCollectionView = require('feed-source-collection-view');
var FeedVersionCollection = require('feed-version-collection');
var FeedVersion = require('feed-version');
var Handlebars = require('handlebars');
var EditableTextWidget = require('editable-text-widget');
var app = require('application');
var ConfirmView = require('confirm-view');
var DeploymentProgressView = require('deployment-progress-view');
var app = require('application');

// FeedVersionItemView is already used on the versions page, so let's keep class names unique
var FeedVersionDeploymentView = Backbone.Marionette.ItemView.extend({
  template: Handlebars.compile(require('./feed-version-deployment-view.html')),
  tagName: 'tr',
  events: {
    'click .remove-version': 'removeVersion',
    'click .use-previous-version': 'usePreviousVersion',
    'click .use-next-version': 'useNextVersion'
  },

  initialize: function () {
    _.bindAll(this, 'removeVersion', 'usePreviousVersion', 'useNextVersion');
  },

  removeVersion: function (e) {
    e.preventDefault();

    this.collection.remove(this.model);
  },

  usePreviousVersion: function (e) {
    e.preventDefault();
    if (this.model.get('previousVersionId') !== null) {
      this.switchVersion(this.model.get('previousVersionId'));
    }
  },

  useNextVersion: function (e) {
    e.preventDefault();
    if (this.model.get('nextVersionId') !== null) {
      this.switchVersion(this.model.get('nextVersionId'));
    }
  },

  /** Utility function to replace this feed version with a different one */
  switchVersion: function (version) {
    var newVersion = new FeedVersion({id: version});
    var instance = this;
    newVersion.fetch({data: {summarized: 'true'}}).done(function () {
      instance.collection.remove(instance.model, {silent: true});
      instance.collection.add(newVersion);
    });
  }
});

module.exports = Backbone.Marionette.CompositeView.extend({
  template: Handlebars.compile(require('./deployment-view.html')),
  childView: FeedVersionDeploymentView,
  childViewContainer: 'tbody',

  events: { 'click .deploy': 'deploy' },

  initialize: function () {
    this.collection = new FeedVersionCollection(this.model.get('feedVersions'));
    _.bindAll(this, 'collectionChange', 'deploy');
  },

  collectionChange: function () {
    this.model.set('feedVersions', this.collection.toJSON());
    this.model.save();
  },

  /**
   * Tell the server to push a deployment to OTP.
   */
   deploy: function (e) {
     // TODO: multiple servers
     // make sure they mean it
     var instance = this;

     app.modalRegion.show(new ConfirmView({
       title: window.Messages('app.confirm'),
       // todo: multiple servers
       body: window.Messages('app.deployment.confirm', instance.model.get('name'), 'Production'),
       onProceed: function () {
         $.post('api/deployments/' + instance.model.id + '/deploy').done(function () {
           // refetch the deployment, to show where it is deployed to
           instance.model.fetch().done(function () {
             instance.render();
             instance.onShow();
           });

           // show the status of the deployment
           // TODO: don't hardcode target
           app.modalRegion.show(new DeploymentProgressView({name: instance.model.get('name'), target: "Production"}));
         });
       }
     }));
   },

  buildChildView: function (child, ChildViewClass, childViewOptions) {
    var opts = _.extend({model: child, collection: this.collection}, childViewOptions);
    return new ChildViewClass(opts);
  },

  onShow: function () {
    // show the invalid feed sources (i.e. sources with no current loadable version)
    this.invalidFeedSourceRegion = new Backbone.Marionette.Region({
      el: '.invalid-feed-sources'
    });

    var invalid = new FeedSourceCollection(this.model.get('invalidFeedSources'));
    this.invalidFeedSourceRegion.show(new FeedSourceCollectionView({collection: invalid, showNewFeedButton: false}));

    // show the name, in an editable fashion
    this.nameRegion = new Backbone.Marionette.Region({
      el: '#deployment-name'
    });

    this.nameRegion.show(new EditableTextWidget({model: this.model, attribute: 'name', href: window.location.hash}));

    this.collection.on('remove', this.collectionChange);
    this.collection.on('add', this.collectionChange);
  }
});
