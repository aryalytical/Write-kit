#!/bin/sh
# One-time developer step: downloads the React preview vendor files into app/src/main/assets/vendor.
# Run this on a machine WITH internet access, then build the app. The app never downloads these at runtime.
set -e
DEST="$(dirname "$0")/../app/src/main/assets/vendor"
mkdir -p "$DEST"
curl -fL -o "$DEST/react.production.min.js"     https://unpkg.com/react@18.3.1/umd/react.production.min.js
curl -fL -o "$DEST/react-dom.production.min.js" https://unpkg.com/react-dom@18.3.1/umd/react-dom.production.min.js
curl -fL -o "$DEST/babel.min.js"                https://unpkg.com/@babel/standalone@7.24.7/babel.min.js
ls -l "$DEST"
