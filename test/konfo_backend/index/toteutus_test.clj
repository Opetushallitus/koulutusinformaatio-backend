(ns konfo-backend.index.toteutus-test
  (:require [clojure.test :refer :all]
            [kouta-indeksoija-service.fixture.kouta-indexer-fixture :as fixture]
            [kouta-indeksoija-service.fixture.external-services :as mocks]
            [konfo-backend.test-tools :refer :all]
            [clj-log.access-log]))

(intern 'clj-log.access-log 'service "konfo-backend")

(use-fixtures :each fixture/mock-indexing-fixture)

(defn toteutus-url
  [oid]
  (apply url-with-query-params (str "/konfo-backend/toteutus/" oid) [:draft false]))

(defn toteutus-draft-url
  [oid]
  (apply url-with-query-params (str "/konfo-backend/toteutus/" oid) [:draft true]))

(deftest toteutus-test

  (let [hakuOid1      "1.2.246.562.29.000001"
        koulutusOid1  "1.2.246.562.13.000001"
        toteutusOid1  "1.2.246.562.17.000001"
        toteutusOid2  "1.2.246.562.17.000002"
        toteutusOid3  "1.2.246.562.17.000003"
        toteutusOid4  "1.2.246.562.17.000004"
        toteutusOid5  "1.2.246.562.17.000005"
        hakukohdeOid1 "1.2.246.562.20.000001"
        hakukohdeOid2 "1.2.246.562.20.000002"
        hakukohdeOid3 "1.2.246.562.20.000003"
        hakukohdeOid4 "1.2.246.562.20.000004"
        hakukohdeOid5 "1.2.246.562.20.000005"
        valintaperusteId1 "2d0651b7-cdd3-463b-80d9-303a60d9616c"
        valintaperusteId2 "45d2ae02-9a5f-42ef-8148-47d07737927b"
        sorakuvausId "2ff6700d-087f-4dbf-9e42-7f38948f227a"]

    (fixture/add-haku-mock hakuOid1 :tila "julkaistu" :organisaatio mocks/Oppilaitos1)

    (fixture/add-koulutus-mock koulutusOid1 :tila "julkaistu" :nimi "Hauska koulutus" :organisaatio mocks/Oppilaitos1 :sorakuvausId sorakuvausId)

    (fixture/add-toteutus-mock toteutusOid1 koulutusOid1 :tila "julkaistu"   :nimi "Hauska toteutus"                :esikatselu "false" :organisaatio mocks/Oppilaitos1)
    (fixture/add-toteutus-mock toteutusOid2 koulutusOid1 :tila "tallennettu" :nimi "Hupaisa julkaisematon toteutus" :esikatselu "false" :organisaatio mocks/Oppilaitos2)
    (fixture/add-toteutus-mock toteutusOid4 koulutusOid1 :tila "tallennettu" :nimi "Tallennettu drafti toteutus" :esikatselu "true" :organisaatio mocks/Oppilaitos2)
    (fixture/add-toteutus-mock toteutusOid5 koulutusOid1 :tila "arkistoitu" :nimi "Arkistoitu toteutus" :esikatselu "true" :organisaatio mocks/Oppilaitos2)

    (fixture/add-hakukohde-mock hakukohdeOid1 toteutusOid1 hakuOid1 :tila "julkaistu"   :organisaatio mocks/Oppilaitos1 :valintaperuste valintaperusteId1)
    (fixture/add-hakukohde-mock hakukohdeOid2 toteutusOid1 hakuOid1 :tila "julkaistu"   :organisaatio mocks/Oppilaitos1 :valintaperuste valintaperusteId2)
    (fixture/add-hakukohde-mock hakukohdeOid3 toteutusOid1 hakuOid1 :tila "tallennettu" :organisaatio mocks/Oppilaitos1 :valintaperuste valintaperusteId1 :esikatselu "true")
    (fixture/add-hakukohde-mock hakukohdeOid4 toteutusOid4 hakuOid1 :tila "tallennettu" :organisaatio mocks/Oppilaitos1 :valintaperuste valintaperusteId1 :esikatselu "true")
    (fixture/add-hakukohde-mock hakukohdeOid5 toteutusOid4 hakuOid1 :tila "tallennettu" :organisaatio mocks/Oppilaitos1 :valintaperuste valintaperusteId1)

    (fixture/add-sorakuvaus-mock sorakuvausId :tila "julkaistu")

    (fixture/index-oids-without-related-indices {:koulutukset [koulutusOid1] :toteutukset [toteutusOid1 toteutusOid2 toteutusOid4 toteutusOid5]})

    (testing "Get toteutus"
      (testing "ok"
        (let [response (get-ok (toteutus-url toteutusOid1))]
          (is (= toteutusOid1 (:oid response)))
          (is (not-any? (fn [hakutieto] (some #(= hakukohdeOid3 (:hakukohdeOid %)) (:hakukohteet hakutieto))) (:hakutiedot response)))))
      (testing "get draft toteutus and hakutiedon hakukohteet when esikatselu true"
        (let [response (get-ok (toteutus-draft-url toteutusOid4))]
          (is (= toteutusOid4 (:oid response)))
          (is (some (fn [hakutieto] (some #(= hakukohdeOid4 (:hakukohdeOid %)) (:hakukohteet hakutieto))) (:hakutiedot response)))
          (is (not-any? (fn [hakutieto] (some #(= hakukohdeOid5 (:hakukohdeOid %)) (:hakukohteet hakutieto))) (:hakutiedot response)))))
      (testing "not found"
        (get-not-found (toteutus-url toteutusOid3)))
      (testing "filter arkistoitu draft even when esikatselu true"
        (get-not-found (toteutus-draft-url toteutusOid5)))
      (testing "filter not julkaistu and draft true but esikatselu false"
        (get-not-found (toteutus-draft-url toteutusOid2)))
      (testing "filter not julkaistu and draft false"
        (get-not-found (toteutus-url toteutusOid2))))))
