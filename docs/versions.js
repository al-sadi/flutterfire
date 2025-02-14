export default {
  // Plugin versions are sourced from the public pub.dev API
  // See website/plugins/source-versions.js for more information on how these are sourced & injected via Webpack
  plugins: {
    firebase_analytics: PUB_FIREBASE_ANALYTICS,
    firebase_analytics_ns: PUB_NS_FIREBASE_ANALYTICS,
    firebase_app_check: PUB_FIREBASE_APP_CHECK,
    firebase_app_check_ns: PUB_NS_FIREBASE_APP_CHECK,
    firebase_auth: PUB_FIREBASE_AUTH,
    firebase_auth_ns: PUB_NS_FIREBASE_AUTH,
    cloud_firestore: PUB_CLOUD_FIRESTORE,
    cloud_firestore_ns: PUB_NS_CLOUD_FIRESTORE,
    cloud_functions: PUB_CLOUD_FUNCTIONS,
    cloud_functions_ns: PUB_NS_CLOUD_FUNCTIONS,
    firebase_messaging: PUB_FIREBASE_MESSAGING,
    firebase_messaging_ns: PUB_NS_FIREBASE_MESSAGING,
    firebase_storage: PUB_FIREBASE_STORAGE,
    firebase_storage_ns: PUB_NS_FIREBASE_STORAGE,
    firebase_core: PUB_FIREBASE_CORE,
    firebase_core_ns: PUB_NS_FIREBASE_CORE,
    firebase_crashlytics: PUB_FIREBASE_CRASHLYTICS,
    firebase_crashlytics_ns: PUB_NS_FIREBASE_CRASHLYTICS,
    firebase_database: PUB_FIREBASE_DATABASE,
    firebase_database_ns: PUB_NS_FIREBASE_DATABASE,
    firebase_dynamic_links: PUB_FIREBASE_DYNAMIC_LINKS,
    firebase_dynamic_links_ns: PUB_NS_FIREBASE_DYNAMIC_LINKS,
    firebase_in_app_messaging: PUB_FIREBASE_IN_APP_MESSAGING,
    firebase_in_app_messaging_ns: PUB_NS_FIREBASE_IN_APP_MESSAGING,
    firebase_ml_vision: PUB_FIREBASE_ML_VISION,
    firebase_performance: PUB_FIREBASE_PERFORMANCE,
    firebase_performance_ns: PUB_NS_FIREBASE_PERFORMANCE,
    firebase_remote_config: PUB_FIREBASE_REMOTE_CONFIG,
    firebase_remote_config_ns: PUB_NS_FIREBASE_REMOTE_CONFIG,
    firebase_app_installations: PUB_NS_FIREBASE_APP_INSTALLATIONS,
    firebase_ml_model_downloader_ns: PUB_NS_FIREBASE_ML_MODEL_DOWNLOADER,
    google_sign_in: "^4.4.4",
  },
  android: {
    google_services: "4.3.8", // com.google.gms:google-services
  },
  web: {
    firebase_cdn: "8.6.1", // https://firebase.google.com/support/release-notes/js
  },
  external: {
    google_sign_in: "^4.5.1",
    flutter_facebook_auth: "^3.5.0",
  }
};
