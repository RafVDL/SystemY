package be.ac.ua.dist.systemy.node.GUI;

import be.ac.ua.dist.systemy.Constants;
import be.ac.ua.dist.systemy.namingServer.NamingServerInterface;
import be.ac.ua.dist.systemy.node.Node;
import be.ac.ua.dist.systemy.node.NodeInterface;
import be.ac.ua.dist.systemy.node.NodeMain;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class NodeController {
    private Node node;
    private String selectedFileName;

    @FXML
    private ListView<String> fileListView;
    @FXML
    private Button deleteLocalBtn;

    @FXML
    private void initialize() {
        node = NodeMain.getNode();

        if (node == null) {
            showNodeStartError();
            Platform.exit();
        }

        fileListView.setItems(node.getAllFilesObservable());
        fileListView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            selectedFileName = newValue;

            // If the file is not downloaded, disable the delete local button
            File selectedFile = new File(Constants.DOWNLOADED_FILES_PATH + selectedFileName);
            if (!selectedFile.isFile()) {
                deleteLocalBtn.setDisable(true);
            }
        });
    }

    private void showNodeStartError() {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText("Node is null");
        alert.setContentText("The node was not initialised when launching the gui. Please restart.");
        alert.showAndWait();
    }

    @FXML
    private void handleOpen() {
        if (selectedFileName != null) {
            File fileToOpen;

            if (node.getLocalFiles().containsKey(selectedFileName)) {
                fileToOpen = new File(Constants.LOCAL_FILES_PATH + selectedFileName);
            } else if (node.getReplicatedFiles().containsKey(selectedFileName)) {
                fileToOpen = new File(Constants.REPLICATED_FILES_PATH + selectedFileName);
            } else {
                node.downloadAFile(selectedFileName);
                fileToOpen = new File(Constants.DOWNLOADED_FILES_PATH + selectedFileName);
                while (!fileToOpen.isFile()) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        System.err.println("Interrupted while waiting file to finish downloading.");
                    }
                }
                deleteLocalBtn.setDisable(true);
            }

            // Contact owner of the file and increase the downloads counter
            try {
                Registry namingServerRegistry = LocateRegistry.getRegistry(node.getNamingServerAddress().getHostAddress(), Constants.RMI_PORT);
                NamingServerInterface namingServerStub = (NamingServerInterface) namingServerRegistry.lookup("NamingServer");
                InetAddress ownerAddress = namingServerStub.getOwner(selectedFileName);

                Registry ownerNodeRegistry = LocateRegistry.getRegistry(ownerAddress.getHostAddress(), Constants.RMI_PORT);
                NodeInterface ownerNodeStub = (NodeInterface) ownerNodeRegistry.lookup("Node");
                ownerNodeStub.increaseDownloads(selectedFileName);

            } catch (RemoteException | UnknownHostException | NotBoundException e) {
                e.printStackTrace();
            }

            // Actually open the file
            try {
                Desktop.getDesktop().open(fileToOpen);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @FXML
    private void handleDelete() {
        if (selectedFileName != null) {
            node.deleteFileFromNetwork(selectedFileName);
            fileListView.getSelectionModel().selectFirst();
        }
    }

    @FXML
    private void handleDeleteLocal() {
        if (selectedFileName != null) {
            node.deleteDownloadedFile(selectedFileName);
        }
    }
}
