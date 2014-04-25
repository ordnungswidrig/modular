;; Copyright © 2014 JUXT LTD.

(ns modular.bidi
  (:require
   [schema.core :as s]
   [modular.ring :refer (RingHandler RingBinding)]
   [com.stuartsierra.component :as component]
   [bidi.bidi :as bidi :refer (match-route path-for)]
   [clojure.tools.logging :refer :all]
))

;; I've thought hard about a less enterprisy name for this protocol, but
;; components that satisfy it fit most definitions of web
;; services. There's an interface (URIs), coupled to an implementation
;; (via handlers)
(defprotocol WebService
  (ring-handler-map [_])
  (routes [_])
  (uri-context [_]))

#_(defn make-handler
  "Create a Ring handler from the route definition data
  structure. Matches a handler from the uri in the request, and invokes
  it with the request as a parameter."
  [route handlers]
  (fn [{:keys [uri] :as request}]
    (let [{:keys [handler params]} (apply match-route route uri (apply concat (seq request)))]
      (when handler ; in this case, handler is a keyword
        (if-let [handler (get handlers handler)]
          (handler (-> request (update-in [:route-params] merge params)))
          (throw
           (ex-info
            (format "Route for (%s) resolved to a key (%s) which was not represented in the handler-map" uri handler)
            {:route route :handler-key handler :handler-keys (keys handlers) :uri uri}))
          )
        ))))

(defrecord WebServiceFromArguments [ring-handler-map routes uri-context]
  WebService
  (ring-handler-map [this] ring-handler-map)
  (routes [this] routes)
  (uri-context [this] uri-context))

(def new-web-service-schema
  {:ring-handler-map {s/Keyword s/Any}
   :routes [(s/one s/Any "pattern") (s/one s/Any "matched")]
   :uri-context s/Str})

(defn new-web-service
  "Create a component with a given set of bidi routes. An optional web
  context can be given as a keyword argument using the :context key. The
  routes can be a bidi route structure, or a function that returns
  one. When using a function, the component is passed as a single
  argument."
  [& {:as opts}]
  (->> opts
       (merge {:uri-context ""} opts)
       (s/validate new-web-service-schema)
       map->WebServiceFromArguments))

;; The ComponentPreference record modifies a bidi route structure to
;; preference a given component when forming a URI from a
;; keyword. Without ComponentPreference, components using identical
;; keywords in their ring-handler-map maps could inadvertantly get in
;; the way of path-for calls from another component.

(defrecord ComponentPreference [matched component]
  bidi/Matched
  (resolve-handler [this m]
    (bidi/resolve-handler matched m))
  (unresolve-handler [this m]
    (if (keyword? (:handler m))
      (or
       ;; In case there's another component using the same key in a handler-map,
       ;; preference a path to an 'internal' handler first.
       (bidi/unresolve-handler matched (assoc m :handler [component (:handler m)]))
       (bidi/unresolve-handler matched m))
      (bidi/unresolve-handler matched m))))

(defn wrap-component-preference
  "Augment the routes entry bound to a request with data indicating the
  component owner of the handler. This information is used to modify the
  behaviour of the path-for function on those routes, such that the
  component owner is preferenced in the case where multiple handlers
  use identical keywords."
  [h component]
  (if (fn? h)
    (fn [req]
      (h (update-in req [::routes] (fn [r] ["" (->ComponentPreference [r] component)]))))
    h))

;; We use ComponentAddressable to allow the formation of URIs using
;; korks in addition to direct reference to handlers, which may be
;; difficult to obtain in modular applications.

;; If a keyword is used, e.g. (bidi/path-for routes :foo) then we try
;; the ring-handler-map of the calling component first, and if there is
;; no handler entry for that keyword, all other components'
;; ring-handler-maps are tried (in an undefined order). This is what is
;; meant by a component 'preference'.

;; If a keyword sequence is used, e.g. (bidi/path-for routes [:foo
;; :bar]) then :foo is interpreted as a dependency of the router
;; (usually a router is shared between multiple components) and :bar
;; is a handler in the ring-handler-map of :foo.

(defrecord ComponentAddressable [matched ckey handlers]
  bidi/Matched
  (resolve-handler [this m]
    (when-let [{:keys [handler] :as res} (bidi/resolve-handler matched m)]
      (if (keyword? handler)
        (assoc res :handler (wrap-component-preference (get-in handlers [ckey handler]) ckey))
        res)))

  (unresolve-handler [this m]
    (cond (coll? (:handler m))
          (when (= ckey (first (:handler m)))
            (bidi/unresolve-handler matched (update-in m [:handler] second)))
          :otherwise (bidi/unresolve-handler matched m))))

(defmacro infof-result [level msg form]
  `(let [res# ~form]
     (logf ~level ~msg (pr-str res#))
     res#))

(defrecord Router []
  component/Lifecycle
  (start [this]
    (let [handlers
          ;; Handlers is a two-level map from dependency key to the
          ;; dependency's handler map.
          (infof-result
           :info "Bidi router determines handlers as %s"
           (apply merge
                  (for [[k v] this
                        :when (satisfies? WebService v)]
                    {k (ring-handler-map v)})))]

      (assoc this
        :handlers handlers
        :routes ["" (vec (for [[ckey v] this
                               :when (satisfies? WebService v)]
                           [(or (uri-context v) "")
                            ;; We wrap in some bidi middleware which
                            ;; allows us to form URIs via a
                            ;; keyword-path: [component-key handler-key]
                            (->ComponentAddressable [(routes v)] ckey handlers)]))])))
  (stop [this] this)

  RingBinding
  (ring-binding [this req] {::routes (:routes this) ::handlers (:handlers this)})

  RingHandler
  (ring-handler [this]
    (-> (:routes this)
        bidi/compile-route
        bidi/make-handler)))

(defn new-router
  "Constructor for a ring handler that collates all bidi routes
  provided by its dependencies."
  []
  (->Router))