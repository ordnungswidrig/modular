;; Copyright © 2014, JUXT LTD. All Rights Reserved.
;;
;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;;     http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.

(defproject juxt.modular/cylon "0.2.3-SNAPSHOT"
  :description "A modular extension that provides secure login through cookie-based and HTTP basic auth with pluggable credential management."
  :url "https://github.com/juxt/modular/tree/master/modules/cylon"
  :license {:name "Apache License, Version 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0.html"}
  :dependencies [[juxt.modular/bidi "0.2.1-SNAPSHOT"]
                 [prismatic/schema "0.2.1"]
                 [ring/ring-core "1.2.2"]
                 [hiccup "1.0.5"]])