React preview needs three files here (they are NOT included in this source tree):
  react.production.min.js
  react-dom.production.min.js
  babel.min.js
Run scripts/fetch_react_vendor.sh once on a machine with internet access, then rebuild the app.
The app itself stays fully offline; these are bundled into the APK at build time.
