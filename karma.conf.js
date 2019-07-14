module.exports = function (config) {
    config.set({
        // We love firefox.
        browsers: ['Firefox'],
        // The directory where the output file lives
        // Must be the same as the :output-to in the build!
        basePath: "target/test-karma/js/compiled/",
        // The file itself
        files: ['test-karma.js'],
        // Tell karma to use the cljs-test framework. Must be installed.
        frameworks: ['cljs-test'],
        // Plugins to be cljs compatible.
        plugins: ['karma-cljs-test', 'karma-firefox-launcher'],
        colors: false,
        logLevel: config.LOG_INFO,
        // Tell to use shadow-cljs things and to keep running
        client: {
            args: ["shadow.test.karma.init"],
            singleRun: false
        }
    })
};

