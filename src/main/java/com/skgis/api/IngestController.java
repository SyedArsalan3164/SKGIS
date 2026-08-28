package com.skgis.api;

import com.skgis.graph.GraphRepository;
import com.skgis.ingestion.CsvReaderService;
import com.skgis.ingestion.GraphWriterService;
import com.skgis.ingestion.PaysimRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ingest")
@CrossOrigin(origins = "*")
public class IngestController {

    private final CsvReaderService csvReaderService;
    private final GraphWriterService graphWriterService;
    private final GraphRepository graphRepository;
    private final String defaultSamplePath;

    public IngestController(CsvReaderService csvReaderService,
                            GraphWriterService graphWriterService,
                            GraphRepository graphRepository,
                            @Value("${skgis.data.sample-path:data/sample/paysim_sample_5000.csv}") String defaultSamplePath) {
        this.csvReaderService = csvReaderService;
        this.graphWriterService = graphWriterService;
        this.graphRepository = graphRepository;
        this.defaultSamplePath = defaultSamplePath;
    }

    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> runIngestion(@RequestParam(required = false) String path) {
        String csvPath = (path != null && !path.isBlank()) ? path : defaultSamplePath;
        try {
            graphRepository.clearGraph();
            List<PaysimRecord> records = csvReaderService.readAndAugmentCsv(csvPath);
            int count = graphWriterService.writeRecords(records);

            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "message", "Ingestion completed successfully",
                    "recordsIngested", count,
                    "filePath", csvPath
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage(),
                    "filePath", csvPath
            ));
        }
    }
}
