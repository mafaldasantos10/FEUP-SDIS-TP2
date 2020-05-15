import java.math.BigInteger;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

public class ProtocolHandler {
    private Peer peer;

    public ProtocolHandler(Peer peer) {
        this.peer = peer;
    }

    public String forwardHandler(String[] request) throws UnknownHostException {
        // FORWARD <file_key> <rep_degree> <body>
        BigInteger fileKey = new BigInteger(request[1]);
        int replicationDegree = Integer.parseInt(request[2]);
        byte[] body = request[3].getBytes(StandardCharsets.UTF_8);

        OutsidePeer outsidePeer = this.peer.getSuccessor();

        if (Helper.middlePeer(fileKey, peer.getId(), peer.getSuccessor().getId())) {

            String message = "BACKUP " + fileKey + " " + replicationDegree + " " + body + "\n";
            try {
                this.peer.sendMessage(message, outsidePeer.getInetSocketAddress());
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

        } else {

            // FORWARD <file_key> <rep_degree> <body>
            String message = "FORWARD " + fileKey + " " + replicationDegree + " " + body + "\n";
            try {
                this.peer.sendMessage(message, outsidePeer.getInetSocketAddress());
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        return "OK";
    }

    public String backupHandler(String[] request) {
        // BACKUP <file_key> <rep_degree> <body>
        try {
            String fileKey = request[1];
            int replicationDegree = Integer.parseInt(request[2]);
            byte[] body = request[3].getBytes(StandardCharsets.UTF_8);

            // TODO ver se o file foi enviado por mim
            if (replicationDegree < 1 || this.peer.getStorage().hasFileStored(new BigInteger(fileKey))) {
                return "OK";
            }

            String fileDirName = this.peer.getBackupDirPath() + "/" + fileKey;

            // Store file
            final Path fileDirPath = Paths.get(fileDirName);

            if (Files.notExists(fileDirPath)) {
                Files.createDirectories(fileDirPath);
            }

            OutputStream outputStream = new FileOutputStream(fileKey);
            outputStream.write(body);
            outputStream.close();

            for (int i = 0; i < request.length; i++) {
                System.out.println(request[i]);
            }

            replicationDegree--;

            if (replicationDegree > 1) {

                OutsidePeer outsidePeer = this.peer.getSuccessor();
                String message = "BACKUP " + fileKey + " " + replicationDegree + " " + body + "\n";
                this.peer.sendMessage(message, outsidePeer.getInetSocketAddress());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return "OK";
    }

    public String restoreHandler(String[] request) throws UnknownHostException {
        // RESTORE <file_key> <ip_address> <port>
        String fileKey = request[1];
        String ipAddress = request[2];
        int port = Integer.parseInt(request[3]);

        if (this.peer.getStorage().hasFileStored(new BigInteger(fileKey))) {
            // deleteFile(fileKey)
            String fileName = this.peer.getBackupDirPath() + "/" + fileKey;
            File file = new File(fileName);
            byte[] body = null;

            try {
                body = Files.readAllBytes(file.toPath());
            } catch (IOException e) {
                e.printStackTrace();
            }

            String message = "GIVECHUNK " + fileKey + body;
            InetSocketAddress inetSocketAddress = new InetSocketAddress(ipAddress, port);
            try {
                this.peer.sendMessage(message, inetSocketAddress);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            String message = "RESTORE " + fileKey + " " + ipAddress + " " + port + "\n";

            try {
                this.peer.sendMessage(message, this.peer.getSuccessor().getInetSocketAddress());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return "OK";
    }

    public String deleteHandler(String[] request) throws UnknownHostException {
        // DELETE <file_key> <ip_address> <port>
        String fileKey = request[1];
        String ipAddress = request[2];
        int port = Integer.parseInt(request[3]);

        if (this.peer.getStorage().hasFileStored(new BigInteger(fileKey)))
            deleteFile(fileKey);

        if (!(ipAddress.equals(this.peer.getAddress().getHostName())) || (this.peer.getPort() != port)) {
            String message = "DELETE " + fileKey + " " + ipAddress + " " + port + "\n";
            try {
                this.peer.sendMessage(message, this.peer.getSuccessor().getInetSocketAddress());
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        return "OK";
    }

    public void deleteFile(String fileId) {
        if (fileId.equals("") || fileId.equals(null)) {
            return;
        }

        File backupDir = new File(this.peer.getBackupDirPath());
        File fileIDDir = new File(backupDir.getPath(), fileId);

        if (fileIDDir.exists()) {
            if (fileIDDir.delete()) {
                System.out.println("File deleted successfully");
            } else {
                System.out.println("Failed to delete the file");
            }
        }

        // Deletes the backup directory if it's empty after the fileID deletion
        File[] backupDirectory = backupDir.listFiles();
        if (backupDirectory.length == 0) {
            backupDir.delete();
        }
    }

    public String GiveChunkHandler(String[] request) throws UnknownHostException {
        // GIVECHUNK <file_key> <body>
        String fileKey = request[1];
        byte[] body = request[2].getBytes(StandardCharsets.UTF_8);
        String folderDirectory = this.peer.getRestoreDirPath();
        String fileDirectory = this.peer.getRestoreDirPath() + "/" + fileKey;

        System.out.println("SAVING File...");
        try {
            final Path filePath = Paths.get(folderDirectory);

            if (Files.notExists(filePath))
                Files.createDirectories(filePath);

            FileOutputStream outputStream = new FileOutputStream(fileDirectory, true);

            outputStream.write(body);
            outputStream.close();

        } catch (IOException e) {
            e.printStackTrace();
        }

        return "OK";
    }
}