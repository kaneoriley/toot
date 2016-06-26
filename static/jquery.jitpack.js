/*
 * Copyright (C) 2016 Kane O'Riley
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
(function($) {
    $.fn.latestVersion = function(group, artifact, callback) {
        if (typeof group !== "string" || typeof artifact !== "string") {
            console.log("Error: group and artifact required.");
            return
        } else if (typeof callback === "undefined") {
            console.log("Error: callback required.");
            return
        }

        var yql_url = 'https://query.yahooapis.com/v1/public/yql';
        var url = 'https://jitpack.io/api/builds/' + group + '/' + artifact + '/latest';

        $.ajax({
            'url': yql_url,
            'data': {
                'q': 'SELECT * FROM json WHERE url="'+url+'"',
                'format': 'json',
                'jsonCompat': 'new'
            },
            'dataType': 'jsonp',
            'success': function(response) {
                try {
                    var version = response.query.results.json.version;
                    if (typeof version !== "undefined") {
                        callback(version)
                    }
                } catch (err) {
                    console.log("Error parsing response: " + err.message);
                }
            }
        });
    };
})(jQuery);