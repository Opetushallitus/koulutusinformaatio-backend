(ns konfo-backend.core-test
  (:require [midje.sweet :refer :all]
            [clj-elasticsearch.elastic-utils :refer [elastic-post]]
            [konfo-backend.core :refer :all]
            [ring.mock.request :as mock]
            [konfo-backend.test-tools :as tools]))

(intern 'clj-log.access-log 'service "konfo-backend")



(fact "Healthcheck API test"
      (let [response (app (mock/request :get "/konfo-backend/healthcheck"))]
          (:status response)
             => 200))
(against-background
      [(before :contents (tools/init-elastic-test))
       (after :contents (tools/stop-elastic-test))]
      (fact "Koulutus 404 search test"
        (let [response (app (mock/request :get "/konfo-backend/koulutus/12323"))]
          (:status response)
            => 404))
      (fact "Oppilaitos 404 search test"
            (let [response (app (mock/request :get "/konfo-backend/oppilaitos/123123"))]
              (:status response)
              => 404))
      (fact "Koulutusmoduuli 404 search test"
            (let [response (app (mock/request :get "/konfo-backend/koulutusmoduuli/12352"))]
              (:status response)
              => 404)))
