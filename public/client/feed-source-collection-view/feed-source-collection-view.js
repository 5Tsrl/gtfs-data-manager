var Backbone = require('backbone');
Backbone.Marionette = require('backbone.marionette');
var $ = require('jquery');
var _ = require('underscore');
var m = require('models');
var Handlebars = require('handlebars');

var EditableTextWidget = require('editabletextwidget');
var FeedSourceView = require('feed-source-view');

/**
 * An editable table of FeedSources
 */
module.exports = Backbone.Marionette.CompositeView.extend({
    childView: FeedSourceView,
    childViewContainer: 'tbody',
    template: Handlebars.compile(require('./feed-source-collection-view.html')),
    
    events: { 'click .newfeedsource': 'add' },
    initialize: function (attr) {
        this.feedCollectionId = attr.feedCollectionId;
        _.bindAll(this, 'add');
    },
    
    add: function () {
        this.collection.add(
            new m.FeedSource({
                name: Messages('app.new_feed_source_name'),
                isPublic: true,
                autofetch: false,
                url: null,
                feedCollection: {id: this.feedCollectionId},
                lastUpdated: 0
            })
        );
    }
});
