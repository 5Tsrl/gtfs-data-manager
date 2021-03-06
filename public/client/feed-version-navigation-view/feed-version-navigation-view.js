var _ = require('underscore');
var FeedVersion = require('feed-version');
var FeedUploadView = require('feed-upload-view');
var FeedSource = require('feed-source');
var app = require('application');
var LayoutView = require('layout-view');

module.exports = LayoutView.extend({
  template: require('./feed-version-navigation-view.html'),

  events: {
    'click .upload-feed': 'uploadFeed',
    'click .update-feed': 'updateFeed'
  },

  // show the feed upload dialog
  uploadFeed: function(e) {
    // model is so that it knows what feed source to upload to
    app.modalRegion.show(new FeedUploadView({
      model: new FeedSource(this.model.get('feedSource'))
    }));
  },

  // fetch the latest version of an autofetched/produced in house feed
  updateFeed: function(e) {
    // user feedback
    var $t = $(e.target);
    $t.find('span.glyphicon').addClass('spinner');
    $t.attr('disabled', true);
    $t.find('span.button-label').text(window.Messages('app.feed_version.updating'));

    var instance = this;
    $.ajax({
      url: 'api/feedsources/' + this.model.get('feedSource').id + '/fetch',
      method: 'POST',
      success: function(data) {
        $t.find('span').removeClass('spinner');
        $t.attr('disabled', false);
        $t.find('span.button-label').text(window.Messages('app.feed_version.update'));

        if (data === null) {
          window.alert('Feed has not changed');
        } else {
          var newVersion = new FeedVersion(data);
          window.location.hash = '#feed/' + newVersion.get('feedSource').id + '/' + newVersion.id;
        }
      }
    });
  },

  initialize: function(attr) {
    if (_.isUndefined(this.model)) {
      // we create a dummy model simply so we don't have to check in the view if the model exists, only if its properties exist
      // and so we can access the feedSource in a uniform way regardless of whether the version exists
      this.model = new FeedVersion({
        feedSource: attr.feedSource.toJSON()
      });
    }

    _.bindAll(this, 'uploadFeed', 'updateFeed');
  },
})
