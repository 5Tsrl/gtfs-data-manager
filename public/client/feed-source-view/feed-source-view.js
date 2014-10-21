/**
 * Show some information about a feed source
 */

var Backbone = require('backbone');
Backbone.Marionette = require('backbone.marionette');
var $ = require('jquery');
var _ = require('underscore');
var Handlebars = require('handlebars.js');
var app = require('application');
var FeedVersion = require('feed-version');
var FeedSource = require('feed-source');
var FeedVersionView = require('feed-version-view');
var FeedUploadView = require('feed-upload-view');

module.exports = Backbone.Marionette.LayoutView.extend({
    template: Handlebars.compile(require("./feed-source-view.html")),
    regions: {validationRegion: '#validation'},

    events: {
        'click .upload-feed': 'uploadFeed',
        'click #share-url': 'doNothing'
    },
    initialize: function (attr) {
        this.feedVersionId = attr.feedVersionId;

        _.bindAll(this, 'uploadFeed');
    },

    // show the feed upload dialog
    uploadFeed: function (e) {
        // model is so that it knows what feed source to upload to
        app.modalRegion.show(new FeedUploadView({model: this.model}));
    },
    
    onShow: function () {
        var version;

        if (this.feedVersionId != undefined && this.feedVersionId != null)
            version = new FeedVersion({id: this.feedVersionId});

        else
            version = new FeedVersion({id: this.model.get('latestVersionId')});

        var instance = this;
        version.fetch().done(function () {
            instance.validationRegion.show(new FeedVersionView({model: version}));
        });

        // expose the copypastable URL to allow users to view/edit
        if (app.user.admin) {
            var instance = this;
            $.ajax({
                url: 'api/feedsources/' + this.model.get('id') + '/getKey',
                success: function (data) {
                    instance.$('#share-url').val(window.location.origin + window.location.pathname + window.location.hash +
                                                 '?userId=' + encodeURIComponent(data['userId']) + '&key=' + encodeURIComponent(data['key']));
                }
            });
        }
    },


    // don't bubble clicks in the input field (e.g. to copy)
    doNothing: function (e) {
        e.preventDefault();
        e.stopPropagation();
    }

})
