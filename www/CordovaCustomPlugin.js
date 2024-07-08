// var exec = require('cordova/exec');

// exports.coolMethod = function (arg0, success, error) {
//     exec(success, error, 'CordovaCustomPlugin', 'coolMethod', [arg0]);
// };

var exec = require('cordova/exec');

var VideoPicker = {
    pickVideo: function(successCallback, errorCallback) {
        exec(successCallback, errorCallback, 'VideoPicker', 'pickVideo', []);
    }
};

module.exports = VideoPicker;