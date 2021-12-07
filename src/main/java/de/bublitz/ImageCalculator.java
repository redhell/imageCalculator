package de.bublitz;

import de.bublitz.model.Point;
import ij.ImagePlus;
import ij.gui.OvalRoi;
import ij.gui.Roi;
import ij.process.ByteProcessor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class ImageCalculator {

    public static void main(String[] args) throws IOException {
        String pfad;
        if (args.length > 0 && args[0] != null) {
            pfad = args[0];
        } else {
            //pfad = "src/main/resources/p45t250/";
            pfad = Paths.get("")
                    .toAbsolutePath()
                    .toString();
        }
        List<Path> filePaths = listFilesUsingFilesList(pfad);

        Map<String, Point> koordinatenMap = new ConcurrentSkipListMap<>();
        // Punkte finden
        filePaths.parallelStream().forEach(path -> koordinatenMap.put(path.getFileName().toString(), getCoordinates(path)));
        List<String> dateiprefix = filePaths.stream()
                .map(path -> path.getFileName().toString().split("_")[0])
                .distinct()
                .collect(Collectors.toList());

        // Distanz berechnen
        Map<String, Double> distanceMap = calculateDistance(filePaths, koordinatenMap);

        // name, Distanz in px, mm*faktor (1mm = 600px)
        String timeStamp = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss").format(new Date());
        FileWriter out = new FileWriter("particals_" + timeStamp + ".csv");
        String[] HEADERS = {"reihe", "name", "distanceInPX", "distanceInMM"};
        try (CSVPrinter printer = new CSVPrinter(out, CSVFormat.DEFAULT
                .withHeader(HEADERS).withDelimiter(';'))) {
            distanceMap.forEach((name, distance) -> {
                try {
                    //if(distance > 30 && distance < 500) {
                    AtomicReference<String> reihe = new AtomicReference<>();
                    dateiprefix.parallelStream().filter(name::contains).findFirst().ifPresent(reihe::set);
                    printer.printRecord(reihe.get(), name, distance, distance / 600);
                    //}
                } catch (IOException e) {
                    log.error(e.getMessage());
                }
            });
        }
        log.info("Objekte: " + koordinatenMap.size());


    }

    private static Map<String, Double> calculateDistance(List<Path> filePaths, Map<String, Point> koordinatenMap) {
        List<String> dateiPrefixe = new ArrayList<>();
        filePaths.forEach(path -> dateiPrefixe.add(path.getFileName().toString().split("_")[0]));
        Map<String, Double> distanceMap = new ConcurrentSkipListMap<>();

        dateiPrefixe.stream().distinct().forEach(dateiName -> {
            for (int i = 0; i < 100; i += 2) {
                String suffix = i > 9 ? String.valueOf(i) : "0" + i;
                String suffix2 = i + 1 > 9 ? String.valueOf(i + 1) : "0" + (i + 1);

                Point p1 = koordinatenMap.get(dateiName + "_00" + suffix + ".TIF");
                Point p2 = koordinatenMap.get(dateiName + "_00" + suffix2 + ".TIF");
                if (p1 != null && p2 != null)
                    distanceMap.put(dateiName + "_00" + suffix + ".TIF", p1.distance(p2));
                else
                    log.warn("Null");
            }
        });
        return distanceMap;
    }

    private static Point getCoordinates(Path pfad) {
        final int radius = 15;
        final int durchmesser = 2 * radius;
        Point koordinaten = new Point();
        ImagePlus imagePlus = new ImagePlus(pfad.toString());
        Roi roi = new OvalRoi(0, 0, durchmesser, durchmesser);
        imagePlus.setRoi(roi);
        imagePlus.setProcessor(new ByteProcessor(imagePlus.getImage()));

        int maxgrau = 0;
        int maxX = 0, maxY = 0;
        for (int y = 0; y <= 1000; y += 10) {
            for (int x = 0; x <= 1000; x += 10) {
                int grauWert = 0;
                imagePlus.getRoi().setLocation(x, y);
                int[] histogramm = imagePlus.getProcessor().getHistogram();
                for (int i = 30; i < 90; i++)
                    grauWert += histogramm[i];
                if (grauWert > maxgrau) {
                    maxgrau = grauWert;
                    maxX = x;
                    maxY = y;
                }
            }
        }
        log.debug("Grauwert: " + maxgrau + " Koordinaten: (" + maxX + ", " + maxY + ")");
        koordinaten.setX(maxX + radius);
        koordinaten.setY(maxY + radius);
        return koordinaten;
    }

    private static List<Path> listFilesUsingFilesList(String dir) throws IOException {
        try (Stream<Path> stream = Files.list(Paths.get(dir))) {
            return stream
                    .filter(file -> !Files.isDirectory(file) && file.toString().contains("TIF"))
                    .collect(Collectors.toList());
        }
    }
}
