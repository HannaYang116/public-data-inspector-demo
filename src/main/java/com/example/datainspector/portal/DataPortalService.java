package com.example.datainspector.portal;

import com.example.datainspector.analyze.AnalyzeService;
import com.example.datainspector.analyze.DatasetPreview;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DataPortalService {
    private static final String BASE_URL = "https://www.data.go.kr";
    private static final Pattern ITEM_PATTERN = Pattern.compile(
            "<a\\s+href=\"/data/(\\d+)/fileData\\.do\">(.*?)<div\\s+class=\"bottom-area\">",
            Pattern.DOTALL
    );
    private static final Pattern TITLE_PATTERN = Pattern.compile("<span\\s+class=\"title\">(.*?)</span>", Pattern.DOTALL);
    private static final Pattern DESCRIPTION_PATTERN = Pattern.compile("<dd\\s+class=\"ellipsis publicDataDesc\">(.*?)</dd>", Pattern.DOTALL);
    private static final Pattern INFO_PATTERN = Pattern.compile("<span\\s+class=\"tit\">(.*?)</span>\\s*<span[^>]*class=?\"?data\"?[^>]*>(.*?)</span>", Pattern.DOTALL);
    private static final Pattern DOWNLOAD_PATTERN = Pattern.compile("fn_fileDataDown\\('(\\d+)',\\s*'(\\d+)'\\)");

    private final AnalyzeService analyzeService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public DataPortalService(AnalyzeService analyzeService) {
        this.analyzeService = analyzeService;
    }

    public List<DataPortalDataset> searchFileDatasets(String keyword) throws IOException, InterruptedException {
        String encodedKeyword = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
        String url = BASE_URL + "/tcs/dss/selectDataSetList.do"
                + "?keyword=" + encodedKeyword
                + "&dType=FILE"
                + "&sort=updtDt"
                + "&currentPage=1";

        String html = sendText(url);
        Matcher matcher = ITEM_PATTERN.matcher(html);
        List<DataPortalDataset> results = new ArrayList<>();

        while (matcher.find() && results.size() < 10) {
            String publicDataPk = matcher.group(1);
            String block = matcher.group(2);
            String title = extract(block, TITLE_PATTERN);
            String description = extract(block, DESCRIPTION_PATTERN);
            String provider = extractInfo(block, "제공기관");
            String updatedDate = extractInfo(block, "수정일");
            String fileDetailSn = extractDownloadSn(block, publicDataPk);

            results.add(new DataPortalDataset(
                    publicDataPk,
                    clean(title),
                    clean(description),
                    clean(provider),
                    clean(updatedDate),
                    BASE_URL + "/data/" + publicDataPk + "/fileData.do",
                    fileDetailSn
            ));
        }

        return results;
    }

    public DatasetPreview analyzeFileDataset(String publicDataPk, String fileDetailSn) throws IOException, InterruptedException {
        DownloadedDataset dataset = downloadFileDataset(publicDataPk, fileDetailSn);
        return analyzeService.analyzeNamedBytes(dataset.filename(), dataset.bytes());
    }

    public DownloadedDataset downloadFileDataset(String publicDataPk, String fileDetailSn) throws IOException, InterruptedException {
        String metadataUrl = BASE_URL + "/tcs/dss/selectFileDataDownload.do"
                + "?publicDataPk=" + encode(publicDataPk)
                + "&fileDetailSn=" + encode(fileDetailSn);

        JsonNode metadata = objectMapper.readTree(sendText(metadataUrl));

        if (!metadata.path("status").asBoolean(false)) {
            throw new IllegalArgumentException("공공데이터포털 파일 다운로드 정보를 가져오지 못했습니다.");
        }

        String atchFileId = metadata.path("atchFileId").asText("");
        String resolvedFileDetailSn = metadata.path("fileDetailSn").asText(fileDetailSn);
        JsonNode detail = metadata.path("dataSetFileDetailInfo");
        String dataName = detail.path("dataNm").asText("public-data-" + publicDataPk);
        String extension = detail.path("atchFileExtsn").asText("");
        String filename = extension.isBlank() ? dataName : dataName + "." + extension;

        String downloadUrl = BASE_URL + "/cmm/cmm/fileDownload.do"
                + "?atchFileId=" + encode(atchFileId)
                + "&fileDetailSn=" + encode(resolvedFileDetailSn)
                + "&dataNm=" + encode(dataName);

        byte[] bytes = sendBytes(downloadUrl);
        return new DownloadedDataset(publicDataPk, filename, bytes);
    }

    private String sendText(String url) throws IOException, InterruptedException {
        return new String(sendBytes(url), StandardCharsets.UTF_8);
    }

    private byte[] sendBytes(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest
                .newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(12))
                .header("User-Agent", "public-data-inspector-demo")
                .GET()
                .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalArgumentException("공공데이터포털 요청에 실패했습니다. HTTP 상태: " + response.statusCode());
        }

        return response.body();
    }

    private String extract(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String extractInfo(String block, String key) {
        Matcher matcher = INFO_PATTERN.matcher(block);

        while (matcher.find()) {
            if (clean(matcher.group(1)).equals(key)) {
                return matcher.group(2);
            }
        }

        return "";
    }

    private String extractDownloadSn(String block, String publicDataPk) {
        Matcher matcher = DOWNLOAD_PATTERN.matcher(block);

        while (matcher.find()) {
            if (matcher.group(1).equals(publicDataPk)) {
                return matcher.group(2);
            }
        }

        return "1";
    }

    private String clean(String html) {
        return html
                .replaceAll("<br\\s*/?>", " ")
                .replaceAll("<[^>]+>", " ")
                .replace("&quot;", "\"")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
