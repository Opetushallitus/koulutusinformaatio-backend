(ns konfo-backend.oppilaitos-test
  (:require [clojure.test :refer :all]
            [midje.sweet :refer :all]
            [konfo-backend.search.oppilaitos :refer :all]
            [clj-log.access-log]))

(facts "search.oppilaitos"
       (fact "oids-by-paikkakunta-query"
             (oids-by-paikkakunta-query "testipaikkakunta")
             => {:bool
                 {:must
                  {:multi_match
                   {:query "testipaikkakunta"
                    :fields ["postiosoite.postitoimipaikka"
                             "kayntiosoite.postitoimipaikka"
                             "yhteystiedot.postitoimipaikka"]
                    :operator "and"}}
                  :must_not
                  {:range
                   {:lakkautusPvm
                    {:format "yyyy-MM-dd"
                     :lt "now"}}}}})

       (fact "oppilaitos-query"
             (oppilaitos-query "keyword" "en" [] {})
             => {:bool
                 {:must
                  [{:dis_max
                    {:queries
                     [{:constant_score
                       {:boost 1000
                        :filter
                               {:multi_match
                                {:fields ["searchData.oppilaitostyyppi.nimi.en"]
                                 :operator "and"
                                 :query "keyword"}}}}
                       {:constant_score
                        {:boost 300
                         :filter
                                {:multi_match
                                 {:fields ["nimi.en"]
                                  :operator "and"
                                  :query "keyword"}}}}
                       {:constant_score
                        {:boost 5
                         :filter
                                {:multi_match
                                 {:fields ["postiosoite.postitoimipaikka"
                                           "kayntiosoite.postitoimipaikka"
                                           "yhteystiedot.postitoimipaikka"]
                                  :operator "and"
                                  :query "keyword"}}}}
                       {:constant_score
                        {:boost 4
                         :filter
                                {:multi_match
                                 {:fields ["postiosoite.osoite"
                                           "kayntiosoite.osoite"
                                           "yhteystiedot.osoite"]
                                  :operator "and"
                                  :query "keyword"}}}}]}}
                    {:dis_max
                     {:queries
                      [{:constant_score
                        {:boost 200
                         :filter
                                {:match
                                 {:tyypit
                                  {:query "Koulutustoimija"}}}}}
                       {:constant_score
                        {:boost 200
                         :filter
                                {:match
                                 {:tyypit
                                  {:query "Oppilaitos"}}}}}
                       {:constant_score
                        {:boost 100
                         :filter
                                {:match
                                 {:tyypit
                                  {:query "Toimipiste"}}}}}]}}]
                  :must_not
                  {:range
                   {:lakkautusPvm
                    {:format "yyyy-MM-dd"
                     :lt "now"}}}}})

       (fact "oppilaitos-query with constraints and oids"
             (oppilaitos-query "keyword" "en" ["oid1 oid2 oid3"]
                               {:kieli "fi,sv" :paikkakunta "testipaikkakunta" :oppilaitostyyppi "kk,ako"})
             => {:bool
                 {:must
                  [{:dis_max
                    {:queries
                     [{:constant_score
                       {:boost 1000
                        :filter
                               {:multi_match
                                {:fields ["searchData.oppilaitostyyppi.nimi.en"]
                                 :operator "and"
                                 :query "keyword"}}}}
                      {:constant_score
                       {:boost 300
                        :filter
                               {:multi_match
                                {:fields ["nimi.en"]
                                 :operator "and"
                                 :query "keyword"}}}}
                      {:constant_score
                       {:boost 4
                        :filter
                               {:multi_match
                                {:fields ["postiosoite.osoite"
                                          "kayntiosoite.osoite"
                                          "yhteystiedot.osoite"]
                                 :operator "and"
                                 :query "keyword"}}}}
                      {:constant_score
                       {:boost 2
                        :filter
                               {:terms
                                {:oid ["oid1 oid2 oid3"]}}}}]}}
                   {:dis_max
                    {:queries
                     [{:constant_score
                       {:boost 200
                        :filter
                               {:match
                                {:tyypit
                                 {:query "Koulutustoimija"}}}}}
                      {:constant_score
                       {:boost 200
                        :filter
                               {:match
                                {:tyypit
                                 {:query "Oppilaitos"}}}}}
                      {:constant_score
                       {:boost 100
                        :filter
                               {:match
                                {:tyypit
                                 {:query "Toimipiste"}}}}}]}}]
                  :must_not
                  {:range
                   {:lakkautusPvm
                    {:format "yyyy-MM-dd"
                     :lt "now"}}}
                  :filter
                  {:multi_match
                   {:fields ["postiosoite.postitoimipaikka"
                             "kayntiosoite.postitoimipaikka"
                             "yhteystiedot.postitoimipaikka"]
                    :operator "and"
                    :query "testipaikkakunta"}}}}))
