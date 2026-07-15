package com.example.datainspector.analyze;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AnalyzeService {
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final int PREVIEW_ROWS = 5;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient;

    public AnalyzeService() {
        this.httpClient = HttpClient
                .newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public DatasetPreview analyzeUpload(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("파일이 비어 있습니다.");
        }

        byte[] bytes = file.getBytes();
        validateSize(bytes.length);

        return analyzeBytes(file.getOriginalFilename(), bytes);
    }

    public DatasetPreview analyzeNamedBytes(String filename, byte[] bytes) throws IOException {
        validateSize(bytes.length);

        return analyzeBytes(filename, bytes);
    }

    public Map<String, Object> analyzeColumnStatistic(String filename, byte[] bytes, String columnName, String statType) throws IOException {
        validateSize(bytes.length);

        String normalizedName = filename == null ? "unknown" : filename;
        String content = decodeText(bytes);
        String extension = extensionOf(normalizedName);
        List<Map<String, Object>> rows;

        if ("json".equals(extension) || looksLikeJson(content)) {
            rows = rowsFromJson(bytes);
        } else if ("csv".equals(extension) || looksLikeCsv(content)) {
            rows = rowsFromCsv(content);
        } else {
            throw new IllegalArgumentException("현재는 CSV와 JSON 파일만 통계 분석할 수 있습니다.");
        }

        if (rows.isEmpty()) {
            throw new IllegalArgumentException("통계를 계산할 데이터가 없습니다.");
        }

        if (rows.stream().noneMatch(row -> row.containsKey(columnName))) {
            throw new IllegalArgumentException("선택한 컬럼을 찾을 수 없습니다: " + columnName);
        }

        return switch (statType) {
            case "VALUE_COUNTS" -> valueCounts(rows, columnName, Integer.MAX_VALUE);
            case "TOP_10" -> valueCounts(rows, columnName, 10);
            case "BLANK_COUNT" -> blankCount(rows, columnName);
            case "NUMBER_SUMMARY" -> numberSummary(rows, columnName);
            case "DATE_DISTRIBUTION" -> dateDistribution(rows, columnName);
            default -> throw new IllegalArgumentException("지원하지 않는 통계 유형입니다: " + statType);
        };
    }

    public DatasetPreview analyzeUrl(String rawUrl) throws IOException, InterruptedException {
        URI uri = validatePublicHttpUrl(rawUrl);

        HttpRequest request = HttpRequest
                .newBuilder(uri)
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", "public-data-inspector-demo")
                .GET()
                .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() >= 300 && response.statusCode() < 400) {
            throw new IllegalArgumentException("리다이렉트 응답은 안전을 위해 지원하지 않습니다.");
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalArgumentException("파일을 가져오지 못했습니다. HTTP 상태: " + response.statusCode());
        }

        byte[] bytes = response.body();
        validateSize(bytes.length);

        String name = uri.getPath() == null || uri.getPath().isBlank() ? uri.getHost() : uri.getPath();
        return analyzeBytes(name, bytes);
    }

    private DatasetPreview analyzeBytes(String filename, byte[] bytes) throws IOException {
        String normalizedName = filename == null ? "unknown" : filename;
        String content = decodeText(bytes);
        String extension = extensionOf(normalizedName);

        if ("json".equals(extension) || looksLikeJson(content)) {
            return analyzeJson(normalizedName, bytes);
        }

        if ("csv".equals(extension) || looksLikeCsv(content)) {
            return analyzeCsv(normalizedName, content);
        }

        throw new IllegalArgumentException("현재는 CSV와 JSON 파일만 분석할 수 있습니다.");
    }

    private String decodeText(byte[] bytes) {
        String utf8 = new String(bytes, StandardCharsets.UTF_8);
        long replacementCount = utf8.chars().filter(ch -> ch == '\uFFFD').count();

        if (replacementCount > 0) {
            return new String(bytes, Charset.forName("MS949"));
        }

        return utf8;
    }

    private DatasetPreview analyzeCsv(String filename, String content) {
        List<List<String>> rows = csvRows(content);

        if (rows.isEmpty()) {
            throw new IllegalArgumentException("CSV 데이터가 비어 있습니다.");
        }

        List<String> columns = rows.getFirst();
        List<Map<String, Object>> previewRows = new ArrayList<>();

        for (List<String> row : rows.stream().skip(1).limit(PREVIEW_ROWS).toList()) {
            Map<String, Object> item = new LinkedHashMap<>();
            for (int i = 0; i < columns.size(); i++) {
                item.put(columns.get(i), i < row.size() ? row.get(i) : "");
            }
            previewRows.add(item);
        }

        return new DatasetPreview(
                filename,
                "CSV",
                columns,
                Math.max(0, rows.size() - 1),
                previewRows,
                inferColumnTypes(previewRows)
        );
    }

    private List<Map<String, Object>> rowsFromCsv(String content) {
        List<List<String>> rows = csvRows(content);

        if (rows.isEmpty()) {
            return List.of();
        }

        List<String> columns = rows.getFirst();
        List<Map<String, Object>> items = new ArrayList<>();

        for (List<String> row : rows.stream().skip(1).toList()) {
            Map<String, Object> item = new LinkedHashMap<>();
            for (int i = 0; i < columns.size(); i++) {
                item.put(columns.get(i), i < row.size() ? row.get(i) : "");
            }
            items.add(item);
        }

        return items;
    }

    private List<List<String>> csvRows(String content) {
        return content
                .lines()
                .filter(line -> !line.isBlank())
                .map(this::parseCsvLine)
                .toList();
    }

    private DatasetPreview analyzeJson(String filename, byte[] bytes) throws IOException {
        JsonNode root = objectMapper.readTree(bytes);
        List<Map<String, Object>> previewRows = new ArrayList<>();
        Set<String> columns = new LinkedHashSet<>();
        int rowCount;

        if (root.isArray()) {
            rowCount = root.size();
            for (JsonNode item : root) {
                if (item.isObject()) {
                    item.fieldNames().forEachRemaining(columns::add);
                }
            }

            for (JsonNode item : root) {
                if (previewRows.size() >= PREVIEW_ROWS) break;
                previewRows.add(jsonObjectToMap(item, columns));
            }
        } else if (root.isObject()) {
            rowCount = 1;
            root.fieldNames().forEachRemaining(columns::add);
            previewRows.add(jsonObjectToMap(root, columns));
        } else {
            throw new IllegalArgumentException("JSON은 객체 또는 객체 배열 형태만 지원합니다.");
        }

        return new DatasetPreview(
                filename,
                "JSON",
                new ArrayList<>(columns),
                rowCount,
                previewRows,
                inferColumnTypes(previewRows)
        );
    }

    private List<Map<String, Object>> rowsFromJson(byte[] bytes) throws IOException {
        JsonNode root = objectMapper.readTree(bytes);
        Set<String> columns = new LinkedHashSet<>();

        if (root.isArray()) {
            for (JsonNode item : root) {
                if (item.isObject()) {
                    item.fieldNames().forEachRemaining(columns::add);
                }
            }

            List<Map<String, Object>> rows = new ArrayList<>();
            for (JsonNode item : root) {
                if (item.isObject()) {
                    rows.add(jsonObjectToMap(item, columns));
                }
            }
            return rows;
        }

        if (root.isObject()) {
            root.fieldNames().forEachRemaining(columns::add);
            return List.of(jsonObjectToMap(root, columns));
        }

        throw new IllegalArgumentException("JSON은 객체 또는 객체 배열 형태만 지원합니다.");
    }

    private Map<String, Object> valueCounts(List<Map<String, Object>> rows, String columnName, int limit) {
        Map<String, Long> counts = rows
                .stream()
                .map(row -> normalizeStatValue(row.get(columnName)))
                .collect(Collectors.groupingBy(value -> value, LinkedHashMap::new, Collectors.counting()));

        List<Map.Entry<String, Long>> sorted = counts
                .entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(limit)
                .toList();

        Map<String, Object> values = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : sorted) {
            values.put(entry.getKey(), entry.getValue());
        }

        return Map.of(
                "rowCount", rows.size(),
                "values", values
        );
    }

    private Map<String, Object> blankCount(List<Map<String, Object>> rows, String columnName) {
        long blanks = rows
                .stream()
                .map(row -> row.get(columnName))
                .filter(value -> value == null || value.toString().isBlank())
                .count();

        return Map.of(
                "rowCount", rows.size(),
                "blankCount", blanks,
                "filledCount", rows.size() - blanks
        );
    }

    private Map<String, Object> numberSummary(List<Map<String, Object>> rows, String columnName) {
        List<Double> numbers = rows
                .stream()
                .map(row -> parseDouble(row.get(columnName)))
                .filter(Objects::nonNull)
                .sorted()
                .toList();

        if (numbers.isEmpty()) {
            throw new IllegalArgumentException("숫자로 분석할 수 있는 값이 없습니다.");
        }

        double sum = numbers.stream().mapToDouble(Double::doubleValue).sum();
        double median = numbers.size() % 2 == 0
                ? (numbers.get(numbers.size() / 2 - 1) + numbers.get(numbers.size() / 2)) / 2
                : numbers.get(numbers.size() / 2);

        return Map.of(
                "count", numbers.size(),
                "blankOrInvalidCount", rows.size() - numbers.size(),
                "sum", sum,
                "average", sum / numbers.size(),
                "min", numbers.getFirst(),
                "max", numbers.getLast(),
                "median", median
        );
    }

    private Map<String, Object> dateDistribution(List<Map<String, Object>> rows, String columnName) {
        Map<String, Long> byMonth = rows
                .stream()
                .map(row -> parseYearMonth(row.get(columnName)))
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(YearMonth::toString, LinkedHashMap::new, Collectors.counting()));

        if (byMonth.isEmpty()) {
            throw new IllegalArgumentException("날짜로 분석할 수 있는 값이 없습니다.");
        }

        return Map.of(
                "rowCount", rows.size(),
                "validDateCount", byMonth.values().stream().mapToLong(Long::longValue).sum(),
                "byMonth", byMonth
        );
    }

    private String normalizeStatValue(Object value) {
        if (value == null || value.toString().isBlank()) {
            return "(빈 값)";
        }

        return value.toString();
    }

    private Double parseDouble(Object value) {
        if (value == null || value.toString().isBlank()) {
            return null;
        }

        try {
            return Double.parseDouble(value.toString().replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private YearMonth parseYearMonth(Object value) {
        if (value == null || value.toString().isBlank()) {
            return null;
        }

        String text = value.toString().trim();
        try {
            return YearMonth.from(LocalDate.parse(text.length() >= 10 ? text.substring(0, 10) : text));
        } catch (DateTimeParseException | StringIndexOutOfBoundsException e) {
            return null;
        }
    }

    private Map<String, Object> jsonObjectToMap(JsonNode item, Set<String> columns) {
        Map<String, Object> row = new LinkedHashMap<>();

        for (String column : columns) {
            JsonNode value = item.get(column);
            if (value == null || value.isNull()) {
                row.put(column, null);
            } else if (value.isNumber()) {
                row.put(column, value.numberValue());
            } else if (value.isBoolean()) {
                row.put(column, value.booleanValue());
            } else {
                row.put(column, value.isValueNode() ? value.asText() : value.toString());
            }
        }

        return row;
    }

    private Map<String, String> inferColumnTypes(List<Map<String, Object>> rows) {
        Map<String, String> types = new LinkedHashMap<>();

        for (Map<String, Object> row : rows) {
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                types.putIfAbsent(entry.getKey(), inferValueType(entry.getValue()));
            }
        }

        return types;
    }

    private String inferValueType(Object value) {
        if (value == null) return "null";
        if (value instanceof Number) return "number";
        if (value instanceof Boolean) return "boolean";

        String text = value.toString();
        if (text.isBlank()) return "blank";
        if (text.matches("-?\\d+(\\.\\d+)?")) return "number";
        if (text.matches("\\d{4}-\\d{2}-\\d{2}.*")) return "date";
        return "text";
    }

    private List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;

        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);

            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == ',' && !quoted) {
                values.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }

        values.add(current.toString().trim());
        return values;
    }

    private URI validatePublicHttpUrl(String rawUrl) throws IOException {
        URI uri;
        try {
            uri = URI.create(rawUrl);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("올바른 URL이 아닙니다.");
        }

        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("HTTP 또는 HTTPS URL만 사용할 수 있습니다.");
        }

        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("호스트가 없는 URL은 사용할 수 없습니다.");
        }

        for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
            if (isBlockedAddress(address)) {
                throw new IllegalArgumentException("내부망 또는 로컬 주소는 분석할 수 없습니다.");
            }
        }

        return uri;
    }

    private boolean isBlockedAddress(InetAddress address) {
        return address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress();
    }

    private void validateSize(int bytes) {
        if (bytes > MAX_BYTES) {
            throw new IllegalArgumentException("파일 크기는 2MB 이하만 지원합니다.");
        }
    }

    private String extensionOf(String filename) {
        int index = filename.lastIndexOf('.');
        if (index < 0) return "";
        return filename.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private boolean looksLikeJson(String content) {
        String trimmed = content.stripLeading();
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }

    private boolean looksLikeCsv(String content) {
        return content.lines().findFirst().orElse("").contains(",");
    }
}
