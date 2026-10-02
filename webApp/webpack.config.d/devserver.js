// OPFS persistence for the SQLite worker needs cross-origin isolation, and the OAuth loopback
// client requires the app to be served from 127.0.0.1 (not localhost).
;(function (config) {
  config.devServer = config.devServer || {}
  config.devServer.host = '127.0.0.1'
  // Read here, when webpack runs, rather than in Gradle at configuration time. The OAuth loopback
  // redirect is derived from the page origin, so any port works; webpack defaults to 8080.
  if (process.env.PORT) config.devServer.port = Number(process.env.PORT)
  config.devServer.historyApiFallback = true
  config.devServer.headers = [
    { key: 'Cross-Origin-Opener-Policy', value: 'same-origin' },
    { key: 'Cross-Origin-Embedder-Policy', value: 'require-corp' },
  ]
})(config)
