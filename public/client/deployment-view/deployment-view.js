var Backbone = require('backbone');
Backbone.Marionette = require('backbone.marionette');
var $ = require('jquery');
var _ = require('underscore');
var FeedCollection = require('feed-collection');
var FeedVersionCollection = require('feed-version-collection');
var Handlebars = require('handlebars');

// FeedVersionItemView is already used on the versions page, so let's keep class names unique
var FeedVersionDeploymentView = Backbone.Marionette.ItemView.extend({
  template: Handlebars.compile(require('./feed-version-deployment-view.html')),
  tagName: 'tr'
});

module.exports = Backbone.Marionette.CompositeView.extend({
  template: Handlebars.compile(require('./deployment-view.html')),
  childView: FeedVersionDeploymentView,
  childViewContainer: 'tbody',

  initialize: function () {
    this.collection = new FeedVersionCollection(this.model.get('feedVersions'));
  }
});
