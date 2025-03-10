package io.jenkins.plugins.restlistparam;

public class TestConst {
  public static final String validTestJson = """
                                             [
                                               {
                                                 "name": "v10.6.4",
                                                 "zipball_url": "https://api.github.com/repos/jellyfin/jellyfin/zipball/v10.6.4",
                                                 "tarball_url": "https://api.github.com/repos/jellyfin/jellyfin/tarball/v10.6.4",
                                                 "commit": {
                                                   "sha": "b49cd1d3017f23fc75703829ac2ea1d45d8a4881",
                                                   "url": "https://api.github.com/repos/jellyfin/jellyfin/commits/b49cd1d3017f23fc75703829ac2ea1d45d8a4881"
                                                 },
                                                 "node_id": "MDM6UmVmMTYxMDEyMDE5OnJlZnMvdGFncy92MTAuNi40"
                                               },
                                               {
                                                 "name": "v10.6.3",
                                                 "zipball_url": "https://api.github.com/repos/jellyfin/jellyfin/zipball/v10.6.3",
                                                 "tarball_url": "https://api.github.com/repos/jellyfin/jellyfin/tarball/v10.6.3",
                                                 "commit": {
                                                   "sha": "16e3bd094f4c7c1b485ef164bf0a32267b7542c0",
                                                   "url": "https://api.github.com/repos/jellyfin/jellyfin/commits/16e3bd094f4c7c1b485ef164bf0a32267b7542c0"
                                                 },
                                                 "node_id": "MDM6UmVmMTYxMDEyMDE5OnJlZnMvdGFncy92MTAuNi4z"
                                               },
                                               {
                                                 "name": "v10.6.2",
                                                 "zipball_url": "https://api.github.com/repos/jellyfin/jellyfin/zipball/v10.6.2",
                                                 "tarball_url": "https://api.github.com/repos/jellyfin/jellyfin/tarball/v10.6.2",
                                                 "commit": {
                                                   "sha": "973fcdbaa11002b5d110bb45d09e7bf218bc3611",
                                                   "url": "https://api.github.com/repos/jellyfin/jellyfin/commits/973fcdbaa11002b5d110bb45d09e7bf218bc3611"
                                                 },
                                                 "node_id": "MDM6UmVmMTYxMDEyMDE5OnJlZnMvdGFncy92MTAuNi4y"
                                               }
                                             ]
                                             """;

  public static final String numberLikeTestJson = """
                                                  [
                                                    {
                                                      "name": "2024.19",
                                                    },
                                                    {
                                                      "name": "2024.20",
                                                    },
                                                    {
                                                      "name": "2024.0",
                                                    }
                                                  ]
                                                  """;

  public static final String numberValuesTestJson = """
                                                    [
                                                      {
                                                        "value": 1.0
                                                      },
                                                      {
                                                        "value": 10
                                                      },
                                                      {
                                                        "value": 11.50
                                                      }
                                                    ]
                                                    """;

  public static final String mixedTypeValuesTestJson = """
                                                       [
                                                         {
                                                           "value": 1.0
                                                         },
                                                         {
                                                           "value": 10
                                                         },
                                                         {
                                                           "value": "11.50"
                                                         },
                                                         {
                                                           "value": true
                                                         },
                                                         {
                                                           "value": false
                                                         }
                                                       ]
                                                       """;

  public static final String validJsonValueItem = """
                                                  {
                                                    "name": "v10.6.4",
                                                    "zipball_url": "https://api.github.com/repos/jellyfin/jellyfin/zipball/v10.6.4",
                                                    "tarball_url": "https://api.github.com/repos/jellyfin/jellyfin/tarball/v10.6.4",
                                                    "commit": {
                                                      "sha": "b49cd1d3017f23fc75703829ac2ea1d45d8a4881",
                                                      "url": "https://api.github.com/repos/jellyfin/jellyfin/commits/b49cd1d3017f23fc75703829ac2ea1d45d8a4881"
                                                    },
                                                    "node_id": "MDM6UmVmMTYxMDEyMDE5OnJlZnMvdGFncy92MTAuNi40"
                                                 }
                                                 """;

  public static final String invalidTestJson = """
                                               [
                                                 {
                                                   "name": "v10.6.4",
                                                   "zipball_url": "https://api.github.com/repos/jellyfin/jellyfin/zipball/v10.6.4",
                                                   "tarball_url": "https://api.github.com/repos/jellyfin/jellyfin/tarball/v10.6.4",
                                                   "commit": {
                                                     "sha": "b49cd1d3017f23fc75703829ac2ea1d45d8a4881",
                                                     "url": "https://api.github.com/repos/jellyfin/jellyfin/commits/b49cd1d3017f23fc75703829ac2ea1d45d8a4881"
                                                   },
                                                   "node_id": "MDM6UmVmMTYxMDEyMDE5OnJlZnMvdGFncy92MTAuNi40"
                                                 },
                                                 {
                                                   "name": "v10.6.3",
                                                   "zipball_url": "https://api.github.com/repos/jellyfin/jellyfin/zipball/v10.6.3",
                                                   "tarball_url": "https://api.github.com/repos/jellyfin/jellyfin/tarball/v10.6.3",
                                                   "commit": {
                                                     "sha": "16e3bd094f4c7c1b485ef164bf0a32267b7542c0",
                                                     "url": "https://api.github.com/repos/jellyfin/jellyfin/commits/16e3bd094f4c7c1b485ef164bf0a32267b7542c0"
                                                   },
                                                   "node_id": "MDM6UmVmMTYxMDEyMDE5OnJlZnMvdGFncy92MTAuNi4z"
                                                 },
                                                 {
                                                   "name": "v10.6.2",
                                                   "zipball_url": "https://api.github.com/repos/jellyfin/jellyfin/zipball/v10.6.2",
                                                   "tarball_url": "https://api.github.com/repos/jellyfin/jellyfin/tarball/v10.6.2",
                                                   "commit": {
                                                     "sha": "973fcdbaa11002b5d110bb45d09e7bf218bc3611",
                                                     "url": "https://api.github.com/repos/jellyfin/jellyfin/commits/973fcdbaa11002b5d110bb45d09e7bf218bc3611"
                                                   },
                                                   "node_id": "MDM6UmVmMTYxMDEyMDE5OnJlZnMvdGFncy92MTAuNi4y"
                                                 }
                                               """;

  public static final String validTestXml = """
                                            <?xml version="1.0" encoding="UTF-8" ?>
                                            <root>
                                              <row>
                                                <name>v10.6.4</name>
                                                <zipball_url>https://api.github.com/repos/jellyfin/jellyfin/zipball/v10.6.4</zipball_url>
                                                <tarball_url>https://api.github.com/repos/jellyfin/jellyfin/tarball/v10.6.4</tarball_url>
                                                <commit>
                                                  <sha>b49cd1d3017f23fc75703829ac2ea1d45d8a4881</sha>
                                                  <url>https://api.github.com/repos/jellyfin/jellyfin/commits/b49cd1d3017f23fc75703829ac2ea1d45d8a4881</url>
                                                </commit>
                                                <node_id>MDM6UmVmMTYxMDEyMDE5OnJlZnMvdGFncy92MTAuNi40</node_id>
                                              </row>
                                              <row>
                                                <name>v10.6.3</name>
                                                <zipball_url>https://api.github.com/repos/jellyfin/jellyfin/zipball/v10.6.3</zipball_url>
                                                <tarball_url>https://api.github.com/repos/jellyfin/jellyfin/tarball/v10.6.3</tarball_url>
                                                <commit>
                                                  <sha>16e3bd094f4c7c1b485ef164bf0a32267b7542c0</sha>
                                                  <url>https://api.github.com/repos/jellyfin/jellyfin/commits/16e3bd094f4c7c1b485ef164bf0a32267b7542c0</url>
                                                </commit>
                                                <node_id>MDM6UmVmMTYxMDEyMDE5OnJlZnMvdGFncy92MTAuNi4z</node_id>
                                              </row>
                                              <row>
                                                <name>v10.6.2</name>
                                                <zipball_url>https://api.github.com/repos/jellyfin/jellyfin/zipball/v10.6.2</zipball_url>
                                                <tarball_url>https://api.github.com/repos/jellyfin/jellyfin/tarball/v10.6.2</tarball_url>
                                                <commit>
                                                  <sha>973fcdbaa11002b5d110bb45d09e7bf218bc3611</sha>
                                                  <url>https://api.github.com/repos/jellyfin/jellyfin/commits/973fcdbaa11002b5d110bb45d09e7bf218bc3611</url>
                                                </commit>
                                                <node_id>MDM6UmVmMTYxMDEyMDE5OnJlZnMvdGFncy92MTAuNi4y</node_id>
                                              </row>
                                            </root>""";

  public static final String invalidTestXml = """
                                              <?xml version="1.0" encoding="UTF-8" ?>
                                              <root>
                                                <row>
                                                  <name>v10.6.4</name>
                                                  <zipball_url>https://api.github.com/repos/jellyfin/jellyfin/zipball/v10.6.4</zipball_url>
                                                  <tarball_url>https://api.github.com/repos/jellyfin/jellyfin/tarball/v10.6.4</tarball_url>
                                                  <commit>
                                                    <sha>b49cd1d3017f23fc75703829ac2ea1d45d8a4881</sha>
                                                    <url>https://api.github.com/repos/jellyfin/jellyfin/commits/b49cd1d3017f23fc75703829ac2ea1d45d8a4881</url>
                                                  </commit>
                                                  <node_id>MDM6UmVmMTYxMDEyMDE5OnJlZnMvdGFncy92MTAuNi40</node_id>
                                                </row>
                                                <row>
                                                  <name>v10.6.3</name>
                                                  <zipball_url>https://api.github.com/repos/jellyfin/jellyfin/zipball/v10.6.3</zipball_url>
                                                  <tarball_url>https://api.github.com/repos/jellyfin/jellyfin/tarball/v10.6.3</tarball_url>
                                                  <commit>
                                                    <sha>16e3bd094f4c7c1b485ef164bf0a32267b7542c0</sha>
                                                    <url>https://api.github.com/repos/jellyfin/jellyfin/commits/16e3bd094f4c7c1b485ef164bf0a32267b7542c0</url>
                                                  </commit>
                                                  <node_id>MDM6UmVmMTYxMDEyMDE5OnJlZnMvdGFncy92MTAuNi4z</node_id>
                                                </row>
                                                <row>
                                                  <name>v10.6.2</name>
                                                  <zipball_url>https://api.github.com/repos/jellyfin/jellyfin/zipball/v10.6.2</zipball_url>
                                                  <tarball_url>https://api.github.com/repos/jellyfin/jellyfin/tarball/v10.6.2</tarball_url>
                                                  <commit>
                                                    <sha>973fcdbaa11002b5d110bb45d09e7bf218bc3611</sha>
                                                    <url>https://api.github.com/repos/jellyfin/jellyfin/commits/973fcdbaa11002b5d110bb45d09e7bf218bc3611</url>
                                                  </commit>
                                                  <node_id>MDM6UmVmMTYxMDEyMDE5OnJlZnMvdGFncy92MTAuNi4y</node_id>
                                                </row>
                                              """;
}
