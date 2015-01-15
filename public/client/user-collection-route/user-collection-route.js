var app = require('application');
var UserCollection = require('user-collection');
var UserCollectionView = require('user-collection-view');

module.exports = function () {
  if (!app.user.admin) {
    window.location.hash = '#';
    return;
  }

  new UserCollection().fetch().done(function (d) {
    d = new UserCollection(d.filter(function (u) {
      return u.autogenerated !== true;
    }));

    app.appRegion.show(new UserCollectionView({collection: d}));
    app.nav.setLocation([
        {name: window.Messages('app.users'), href: '#users'}
      ]);
  });
};
