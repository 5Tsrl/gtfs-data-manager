var Backbone = require('backbone');
Backbone.Marionette = require('backbone.marionette');
var $ = require('jquery');
var _ = require('underscore');
var m = require('models');
var Handlebars = require('handlebars');

var EditableText = require('editabletext');

/**
 * An item view of a single FeedSource
 */
var FeedSourceItemView = Backbone.Marionette.LayoutView.extend({
    regions: {
        nameRegion: '.name',
        urlRegion: '.url'
    },
    template: Handlebars.compile(require('./FeedSource.html')),
    tagName: 'tr',

    events: { 'click .edit-bool': 'editBool' },
    initialize: function () {
        _.bindAll(this, 'editBool');
    },

    // edit a boolean value
    editBool: function (e) {
        var $t = $(e.target);

        var attr = {};
        attr[$t.attr('name')] = $t.is(':checked');
        
        this.model.set(attr);
        this.model.save();

        // no need to re-render because the checkbox has already been rendered by the browser
    },

    onShow: function () {
        var nameField = new EditableText({
            model: this.model,
            attribute: 'name', 
            href: function () {
                if (this.model.id == null) {
                    // make it a no-op until saved
                    return '#overview/' + this.model.get('feedCollection').id;
                }
                else {
                    return '#feed/' + this.model.get('id');
                }
            }
        });
        this.nameRegion.show(nameField);

        // start out editing name if it's new; this way we ensure it is saved before 
        if (this.model.get('id') == null)
            nameField.edit();

        this.urlRegion.show(new EditableText({
            model: this.model,
            attribute: 'url',
            href: function () { return this.model.get('url') }
        }));
    }
});

/**
 * An editable table of FeedSources
 */
module.exports.FeedSourceEditableView = Backbone.Marionette.CompositeView.extend({
    childView: FeedSourceItemView,
    childViewContainer: 'tbody',
    template: Handlebars.compile(require('./FeedSourceTable.html')),
    
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
