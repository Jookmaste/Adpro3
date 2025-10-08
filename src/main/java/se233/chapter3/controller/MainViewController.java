package se233.chapter3.controller;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.scene.input.KeyEvent;
import se233.chapter3.Launcher;
import se233.chapter3.model.FileFreq;
import se233.chapter3.model.PdfDocument;


import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainViewController {

    private static final Logger logger = LoggerFactory.getLogger(MainViewController.class);

    Map<String, String> displayToKeyMap;

    private Map<String, String> filenameToPathMap = new LinkedHashMap<>();

    LinkedHashMap<String, List<FileFreq>>uniqueSets;

    @FXML
    private MenuItem closeMenuItem;
    @FXML
    private ListView<String>inputListView;
    @FXML
    private Button startButton;
    @FXML
    private ListView listView;


    public void initialize() {
        inputListView.setOnDragOver(event-> {
            Dragboard db = event.getDragboard();
            final boolean isAccepted = db.getFiles().get(0).getName().toLowerCase().endsWith(".pdf");
            if (db.hasFiles() && isAccepted) {
                event.acceptTransferModes(TransferMode.COPY);
            } else {
                event.consume();
            }
        });
        inputListView.setOnDragDropped(event->{
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                success = true;
                for (File file : db.getFiles()) {
                    String filename = file.getName();
                    String path = file.getAbsolutePath();
                    inputListView.getItems().add(filename);
                    filenameToPathMap.put(filename, path); // <<<< mapping
                }
                event.setDropCompleted(success);
                event.consume();
            }
        });
        startButton.setOnAction(event-> {

            List<String> fileNames = inputListView.getItems();

            logger.info("Starting indexing process for files: {}", fileNames);

            Parent bgRoot = Launcher.primaryStage.getScene().getRoot();
            Task<Void> processTask = new Task<Void>() {
                @Override
                public Void call() throws IOException {
                    ProgressIndicator pi = new ProgressIndicator();
                    VBox box = new VBox(pi);
                    box.setAlignment(Pos.CENTER);
                    Launcher.primaryStage.getScene().setRoot(box);
            ExecutorService executor = Executors.newFixedThreadPool(4);
            final ExecutorCompletionService<Map<String,FileFreq>> completionService = new ExecutorCompletionService<>(executor);

            List<String> inputListViewItems = inputListView.getItems();
            int total_files = inputListViewItems.size();
            Map<String, FileFreq>[] wordMap = new Map[total_files];
            for (int i = 0; i < total_files; i++) {
                try {
//                    String filePath = inputListViewItems.get(i);
                    String filename = inputListViewItems.get(i);
                    String filePath = filenameToPathMap.get(filename);
                    PdfDocument p = new PdfDocument(filePath);
                    completionService.submit(new WordCountMapTask(p));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            for(int i=0; i < total_files; i++){
                try{
                    Future<Map<String,FileFreq>> future= completionService.take();
                    wordMap[i]=future.get();
                } catch(Exception e){
                    e.printStackTrace();
                }
            }
            try{
            WordCountReduceTask merger = new WordCountReduceTask(wordMap);
            Future<LinkedHashMap<String, List<FileFreq>>> future =executor.submit(merger);
            uniqueSets =future.get();

            listView.getItems().addAll(uniqueSets.keySet());

                List<String> displayList = new ArrayList<>();
                displayToKeyMap = new HashMap<>();

                for (Map.Entry<String, List<FileFreq>> entry : uniqueSets.entrySet()) {
                    String word = entry.getKey();
                    List<FileFreq> freqs = entry.getValue();
                    List<Integer> freqCounts = freqs.stream()
                            .map(FileFreq::getFreq)
                            .sorted(Comparator.reverseOrder())
                            .collect(Collectors.toList());
                    String display = word + " (" + freqCounts.stream().map(String::valueOf).collect(Collectors.joining(", ")) + ")";
                    displayList.add(display);
                    displayToKeyMap.put(display, word);
                }
                listView.getItems().clear();
                listView.getItems().addAll(displayList);

            }catch (Exception e){
                e.printStackTrace();
                }finally {
                executor.shutdown();
            }
                    return null;
                }
            };
            processTask.setOnSucceeded( e-> {
                Launcher.primaryStage.getScene().setRoot(bgRoot);
            });
            Thread thread = new Thread(processTask);
            thread.setDaemon(true);
            thread.start();
        });
        listView.setOnMouseClicked(event-> {
//            List<FileFreq> listOfLinks = uniqueSets.get(listView.getSelectionModel().getSelectedItem());

            String selectedDisplay = (String) listView.getSelectionModel().getSelectedItem();
            if (selectedDisplay == null) return;
            String realKey = displayToKeyMap.get(selectedDisplay);
            if (realKey == null) return;
            List<FileFreq> listOfLinks = uniqueSets.get(realKey);
            if (listOfLinks == null) return;

            ListView<FileFreq> popupListView = new ListView<>();
            LinkedHashMap<FileFreq,String> lookupTable = new LinkedHashMap<>();
            for (int i=0 ; i<listOfLinks.size() ; i++) {
                lookupTable.put(listOfLinks.get(i), listOfLinks.get(i).getPath());
                popupListView.getItems().add(listOfLinks.get(i));
            }
            popupListView.setPrefWidth(Region.USE_COMPUTED_SIZE);
            popupListView.setPrefHeight(popupListView.getItems().size() * 40);
            popupListView.setOnMouseClicked(innerEvent-> {
                Launcher.hs.showDocument("file:///"+lookupTable.get(popupListView.
                        getSelectionModel().getSelectedItem()));
                popupListView.getScene().getWindow().hide();
            });
            Popup popup = new Popup();
            popup.getContent().add(popupListView);
            popup.show(Launcher.primaryStage);
            popupListView.setOnKeyPressed(keysevent -> {
                if (keysevent.getCode() == KeyCode.ESCAPE) {
                    popup.hide();
                }
            });
        });

        closeMenuItem.setOnAction(event -> {
            javafx.application.Platform.exit();
        });
    }
}