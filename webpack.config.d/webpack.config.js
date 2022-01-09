(function(config) {
    config.performance = {
        hints: false,
    };
    const HtmlWebpackPlugin = require('html-webpack-plugin');
    const InlineChunkHtmlPlugin = require("react-dev-utils/InlineChunkHtmlPlugin");

    config.plugins.push(new HtmlWebpackPlugin({
       template: '../../../distributions/template.html',
       inject: 'body',
       cache: false,
       entry: "salary.js",
       filename: 'index.html',
       minify: false
    }))
    config.plugins.push(new InlineChunkHtmlPlugin(HtmlWebpackPlugin, [/\.(js|css)$/]),)
})(config)