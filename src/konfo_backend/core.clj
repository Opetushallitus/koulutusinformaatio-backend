(ns konfo-backend.core
  (:require
    [konfo-backend.toteutus :as toteutus]
    [konfo-backend.koulutus :as koulutus]
    [konfo-backend.oppilaitos :as organisaatio]
    [konfo-backend.search.search :as search]
    [konfo-backend.palaute.palaute :as palaute]
    [konfo-backend.config :refer [config]]
    [clj-log.access-log :refer [with-access-logging]]
    [compojure.api.sweet :refer :all]
    [ring.middleware.cors :refer [wrap-cors]]
    [ring.util.http-response :refer :all]
    [environ.core :refer [env]]
    [clojure.tools.logging :as log]))

(defn init []
  (if-let [elastic-url (:elastic-url config)]
    (intern 'clj-elasticsearch.elastic-utils 'elastic-host elastic-url)
    (throw (IllegalStateException. "Could not read elastic-url from configuration!")))
  (intern 'clj-log.access-log 'service "konfo-backend")
  (intern 'clj-log.error-log 'test false))

(def konfo-api
  (api
    {:swagger {:ui   "/konfo-backend"
               :spec "/konfo-backend/swagger.json"
               :data {:info {:title       "Konfo-backend"
                             :description "Backend for Konfo koulutusinformaatio UI."}}}
     ;;:exceptions {:handlers {:compojure.api.exception/default logging/error-handler*}}
     }
    (context "/konfo-backend" []
      (GET "/healthcheck" [:as request]
        :summary "Healthcheck API"
        (with-access-logging request (ok "OK")))

      (context "/search" []
        (GET "/totetukset" [:as request]
          :summary "Koulutusten toteutukset search API"
          :query-params [{keyword :- String nil}
                        {page :- Long 1}
                        {size :- Long 20}
                        {koulutustyyppi :- String nil}
                        {paikkakunta :- String nil}
                        {kieli :- String nil}
                        {lng :- String "fi"}]
          (with-access-logging request (ok (search/search-toteutus keyword lng page size
                                                                   (search/constraints :koulutustyyppi koulutustyyppi
                                                                                       :paikkakunta paikkakunta
                                                                                       :kieli kieli)))))

        (GET "/koulutukset" [:as request]
          :summary "Koulutus search API"
          :query-params [{keyword :- String nil}
                         {page :- Long 1}
                         {size :- Long 20}
                         {koulutustyyppi :- String nil}
                         {paikkakunta :- String nil}
                         {kieli :- String nil}
                         {lng :- String "fi"}]
          (with-access-logging request (ok (search/search-koulutus keyword lng page size
                                                                   (search/constraints :koulutustyyppi koulutustyyppi
                                                                                       :paikkakunta paikkakunta
                                                                                       :kieli kieli)))))

        (GET "/oppilaitokset" [:as request]
          :summary "Oppilaitokset search API"
          :query-params [{keyword :- String nil}
                         {page :- Long 1}
                         {size :- Long 20}
                         {koulutustyyppi :- String nil}
                         {paikkakunta :- String nil}
                         {kieli :- String nil}
                         {lng :- String "fi"}]
          (with-access-logging request (if (some #{lng} ["fi" "sv" "en"])
                                         (ok (search/search-oppilaitos keyword lng page size
                                                                       (search/constraints :koulutustyyppi koulutustyyppi
                                                                                           :paikkakunta paikkakunta
                                                                                           :kieli kieli)))
                                         (bad-request "Invalid lng")))))

      (GET "/oppilaitos/:oid" [:as request]
        :summary "Oppilaitos API"
        :path-params [oid :- String]
        (with-access-logging request (if-let [res (organisaatio/get-oppilaitos oid)]
                                       (ok {:result res})
                                       (not-found "Not found"))))

      (GET "/toteutus/:oid" [:as request]
        :summary "Koulutuksen toteutus API"
        :path-params [oid :- String]
        (with-access-logging request (if (not (nil? oid))
                                       (if-let [res (toteutus/get-toteutus oid)]
                                         (ok {:result res})
                                         (not-found "Not found"))
                                       (bad-request "OID missing"))))

      (GET "/koulutus/:oid" [:as request]
        :summary "Koulutus API"
        :path-params [oid :- String]
        (with-access-logging request (if-let [res (koulutus/get-koulutus oid)]
                                       (ok {:result res})
                                       (not-found "Not found"))))

      (GET "/palaute" [:as request]
        :summary "GET palautteet"
        :query-params [{after :- Long 0}]
        (with-access-logging request (ok (palaute/get-palautteet after))))

      (POST "/palaute" [:as request]
        :summary "POST palaute"
        :form-params [{arvosana :- Long nil}
                      {palaute :- String ""}]
        (with-access-logging request (if (and arvosana (<= 1 arvosana 5))
                                       (ok {:result (palaute/post-palaute arvosana palaute)})
                                       (bad-request "Invalid arvosana")))))))

(def app
  (wrap-cors konfo-api :access-control-allow-origin [#".*"] :access-control-allow-methods [:get :post]))
