package io.jenkins.plugins.restlistparam;

public class TestConst {
  public static final String validTestJson = "[\n" +
    "  {\n" +
    "    \"name\": \"v10.6.4\",\n" +
    "    \"zipball_url\": \"https://api.github.com/repos/jellyfin/jellyfin/zipball/v10.6.4\",\n" +
    "    \"tarball_url\": \"https://api.github.com/repos/jellyfin/jellyfin/tarball/v10.6.4\",\n" +
    "    \"commit\": {\n" +
    "      \"sha\": \"b49cd1d3017f23fc75703829ac2ea1d45d8a4881\",\n" +
    "      \"url\": \"https://api.github.com/repos/jellyfin/jellyfin/commits/b49cd1d3017f23fc75703829ac2ea1d45d8a4881\"\n" +
    "    },\n" +
    "    \"node_id\": \"MDM6UmVmMTYxMDEyMDE5OnJlZnMvdGFncy92MTAuNi40\"\n" +
    "  },\n" +
    "  {\n" +
    "    \"name\": \"v10.6.3\",\n" +
    "    \"zipball_url\": \"https://api.github.com/repos/jellyfin/jellyfin/zipball/v10.6.3\",\n" +
    "    \"tarball_url\": \"https://api.github.com/repos/jellyfin/jellyfin/tarball/v10.6.3\",\n" +
    "    \"commit\": {\n" +
    "      \"sha\": \"16e3bd094f4c7c1b485ef164bf0a32267b7542c0\",\n" +
    "      \"url\": \"https://api.github.com/repos/jellyfin/jellyfin/commits/16e3bd094f4c7c1b485ef164bf0a32267b7542c0\"\n" +
    "    },\n" +
    "    \"node_id\": \"MDM6UmVmMTYxMDEyMDE5OnJlZnMvdGFncy92MTAuNi4z\"\n" +
    "  },\n" +
    "  {\n" +
    "    \"name\": \"v10.6.2\",\n" +
    "    \"zipball_url\": \"https://api.github.com/repos/jellyfin/jellyfin/zipball/v10.6.2\",\n" +
    "    \"tarball_url\": \"https://api.github.com/repos/jellyfin/jellyfin/tarball/v10.6.2\",\n" +
    "    \"commit\": {\n" +
    "      \"sha\": \"973fcdbaa11002b5d110bb45d09e7bf218bc3611\",\n" +
    "      \"url\": \"https://api.github.com/repos/jellyfin/jellyfin/commits/973fcdbaa11002b5d110bb45d09e7bf218bc3611\"\n" +
    "    },\n" +
    "    \"node_id\": \"MDM6UmVmMTYxMDEyMDE5OnJlZnMvdGFncy92MTAuNi4y\"\n" +
    "  }\n" +
    "]\n";

  public static final String numberLikeTestJson = "[\n" +
    "  {\n" +
    "    \"name\": \"2024.19\",\n" +
    "  },\n" +
    "  {\n" +
    "    \"name\": \"2024.20\",\n" +
    "  },\n" +
    "  {\n" +
    "    \"name\": \"2024.0\",\n" +
    "  }\n" +
    "]\n";

  public static final String numberValuesTestJson = "[\n" +
    "  {\n" +
    "    \"value\": 1.0\n" +
    "  },\n" +
    "  {\n" +
    "    \"value\": 10\n" +
    "  },\n" +
    "  {\n" +
    "    \"value\": 11.50\n" +
    "  }\n" +
    "]\n";

  public static final String mixedTypeValuesTestJson = "[\n" +
    "  {\n" +
    "    \"value\": 1.0\n" +
    "  },\n" +
    "  {\n" +
    "    \"value\": 10\n" +
    "  },\n" +
    "  {\n" +
    "    \"value\": \"11.50\"\n" +
    "  },\n" +
    "  {\n" +
    "    \"value\": true\n" +
    "  },\n" +
    "  {\n" +
    "    \"value\": false\n" +
    "  }\n" +
    "]\n";

  public static final String validJsonValueItem = "{" +
    "\"name\":\"v10.6.4\"," +
    "\"zipball_url\":\"https://api.github.com/repos/jellyfin/jellyfin/zipball/v10.6.4\"," +
    "\"tarball_url\":\"https://api.github.com/repos/jellyfin/jellyfin/tarball/v10.6.4\"," +
    "\"commit\":{" +
    "\"sha\":\"b49cd1d3017f23fc75703829ac2ea1d45d8a4881\"," +
    "\"url\":\"https://api.github.com/repos/jellyfin/jellyfin/commits/b49cd1d3017f23fc75703829ac2ea1d45d8a4881\"" +
    "}," +
    "\"node_id\":\"MDM6UmVmMTYxMDEyMDE5OnJlZnMvdGFncy92MTAuNi40\"" +
    "}";

  public static final String invalidTestJson = "[\n" +
    "  {\n" +
    "    \"name\": \"v10.6.4\",\n" +
    "    \"zipball_url\": \"https://api.github.com/repos/jellyfin/jellyfin/zipball/v10.6.4\",\n" +
    "    \"tarball_url\": \"https://api.github.com/repos/jellyfin/jellyfin/tarball/v10.6.4\",\n" +
    "    \"commit\": {\n" +
    "      \"sha\": \"b49cd1d3017f23fc75703829ac2ea1d45d8a4881\",\n" +
    "      \"url\": \"https://api.github.com/repos/jellyfin/jellyfin/commits/b49cd1d3017f23fc75703829ac2ea1d45d8a4881\"\n" +
    "    },\n" +
    "    \"node_id\": \"MDM6UmVmMTYxMDEyMDE5OnJlZnMvdGFncy92MTAuNi40\"\n" +
    "  },\n" +
    "  {\n" +
    "    \"name\": \"v10.6.3\",\n" +
    "    \"zipball_url\": \"https://api.github.com/repos/jellyfin/jellyfin/zipball/v10.6.3\",\n" +
    "    \"tarball_url\": \"https://api.github.com/repos/jellyfin/jellyfin/tarball/v10.6.3\",\n" +
    "    \"commit\": {\n" +
    "      \"sha\": \"16e3bd094f4c7c1b485ef164bf0a32267b7542c0\",\n" +
    "      \"url\": \"https://api.github.com/repos/jellyfin/jellyfin/commits/16e3bd094f4c7c1b485ef164bf0a32267b7542c0\"\n" +
    "    },\n" +
    "    \"node_id\": \"MDM6UmVmMTYxMDEyMDE5OnJlZnMvdGFncy92MTAuNi4z\"\n" +
    "  },\n" +
    "  {\n" +
    "    \"name\": \"v10.6.2\",\n" +
    "    \"zipball_url\": \"https://api.github.com/repos/jellyfin/jellyfin/zipball/v10.6.2\",\n" +
    "    \"tarball_url\": \"https://api.github.com/repos/jellyfin/jellyfin/tarball/v10.6.2\",\n" +
    "    \"commit\": {\n" +
    "      \"sha\": \"973fcdbaa11002b5d110bb45d09e7bf218bc3611\",\n" +
    "      \"url\": \"https://api.github.com/repos/jellyfin/jellyfin/commits/973fcdbaa11002b5d110bb45d09e7bf218bc3611\"\n" +
    "    },\n" +
    "    \"node_id\": \"MDM6UmVmMTYxMDEyMDE5OnJlZnMvdGFncy92MTAuNi4y\"\n" +
    "  }\n";

  public static final String validTestXml = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
    "<root>\n" +
    "  <row>\n" +
    "    <name>v10.6.4</name>\n" +
    "    <zipball_url>https://api.github.com/repos/jellyfin/jellyfin/zipball/v10.6.4</zipball_url>\n" +
    "    <tarball_url>https://api.github.com/repos/jellyfin/jellyfin/tarball/v10.6.4</tarball_url>\n" +
    "    <commit>\n" +
    "      <sha>b49cd1d3017f23fc75703829ac2ea1d45d8a4881</sha>\n" +
    "      <url>https://api.github.com/repos/jellyfin/jellyfin/commits/b49cd1d3017f23fc75703829ac2ea1d45d8a4881</url>\n" +
    "    </commit>\n" +
    "    <node_id>MDM6UmVmMTYxMDEyMDE5OnJlZnMvdGFncy92MTAuNi40</node_id>\n" +
    "  </row>\n" +
    "  <row>\n" +
    "    <name>v10.6.3</name>\n" +
    "    <zipball_url>https://api.github.com/repos/jellyfin/jellyfin/zipball/v10.6.3</zipball_url>\n" +
    "    <tarball_url>https://api.github.com/repos/jellyfin/jellyfin/tarball/v10.6.3</tarball_url>\n" +
    "    <commit>\n" +
    "      <sha>16e3bd094f4c7c1b485ef164bf0a32267b7542c0</sha>\n" +
    "      <url>https://api.github.com/repos/jellyfin/jellyfin/commits/16e3bd094f4c7c1b485ef164bf0a32267b7542c0</url>\n" +
    "    </commit>\n" +
    "    <node_id>MDM6UmVmMTYxMDEyMDE5OnJlZnMvdGFncy92MTAuNi4z</node_id>\n" +
    "  </row>\n" +
    "  <row>\n" +
    "    <name>v10.6.2</name>\n" +
    "    <zipball_url>https://api.github.com/repos/jellyfin/jellyfin/zipball/v10.6.2</zipball_url>\n" +
    "    <tarball_url>https://api.github.com/repos/jellyfin/jellyfin/tarball/v10.6.2</tarball_url>\n" +
    "    <commit>\n" +
    "      <sha>973fcdbaa11002b5d110bb45d09e7bf218bc3611</sha>\n" +
    "      <url>https://api.github.com/repos/jellyfin/jellyfin/commits/973fcdbaa11002b5d110bb45d09e7bf218bc3611</url>\n" +
    "    </commit>\n" +
    "    <node_id>MDM6UmVmMTYxMDEyMDE5OnJlZnMvdGFncy92MTAuNi4y</node_id>\n" +
    "  </row>\n" +
    "</root>";

  public static final String invalidTestXml = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
    "<root>\n" +
    "  <row>\n" +
    "    <name>v10.6.4</name>\n" +
    "    <zipball_url>https://api.github.com/repos/jellyfin/jellyfin/zipball/v10.6.4</zipball_url>\n" +
    "    <tarball_url>https://api.github.com/repos/jellyfin/jellyfin/tarball/v10.6.4</tarball_url>\n" +
    "    <commit>\n" +
    "      <sha>b49cd1d3017f23fc75703829ac2ea1d45d8a4881</sha>\n" +
    "      <url>https://api.github.com/repos/jellyfin/jellyfin/commits/b49cd1d3017f23fc75703829ac2ea1d45d8a4881</url>\n" +
    "    </commit>\n" +
    "    <node_id>MDM6UmVmMTYxMDEyMDE5OnJlZnMvdGFncy92MTAuNi40</node_id>\n" +
    "  </row>\n" +
    "  <row>\n" +
    "    <name>v10.6.3</name>\n" +
    "    <zipball_url>https://api.github.com/repos/jellyfin/jellyfin/zipball/v10.6.3</zipball_url>\n" +
    "    <tarball_url>https://api.github.com/repos/jellyfin/jellyfin/tarball/v10.6.3</tarball_url>\n" +
    "    <commit>\n" +
    "      <sha>16e3bd094f4c7c1b485ef164bf0a32267b7542c0</sha>\n" +
    "      <url>https://api.github.com/repos/jellyfin/jellyfin/commits/16e3bd094f4c7c1b485ef164bf0a32267b7542c0</url>\n" +
    "    </commit>\n" +
    "    <node_id>MDM6UmVmMTYxMDEyMDE5OnJlZnMvdGFncy92MTAuNi4z</node_id>\n" +
    "  </row>\n" +
    "  <row>\n" +
    "    <name>v10.6.2</name>\n" +
    "    <zipball_url>https://api.github.com/repos/jellyfin/jellyfin/zipball/v10.6.2</zipball_url>\n" +
    "    <tarball_url>https://api.github.com/repos/jellyfin/jellyfin/tarball/v10.6.2</tarball_url>\n" +
    "    <commit>\n" +
    "      <sha>973fcdbaa11002b5d110bb45d09e7bf218bc3611</sha>\n" +
    "      <url>https://api.github.com/repos/jellyfin/jellyfin/commits/973fcdbaa11002b5d110bb45d09e7bf218bc3611</url>\n" +
    "    </commit>\n" +
    "    <node_id>MDM6UmVmMTYxMDEyMDE5OnJlZnMvdGFncy92MTAuNi4y</node_id>\n" +
    "  </row>\n";
}
