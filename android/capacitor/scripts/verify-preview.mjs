// Reuse the screen verification used by the phase mocks, targeting the actual packaged bundle.
process.env.KNOW_ANDROID_BUNDLE_PATH = "android/capacitor/dist/index.html";
await import("../../../webapp/design/verify-android-preview.mjs");
