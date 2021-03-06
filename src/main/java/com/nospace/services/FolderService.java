package com.nospace.services;

import com.nospace.dtos.FolderDto;
import com.nospace.dtos.mappers.FolderMapperImpl;
import com.nospace.entities.File;
import com.nospace.exception.FolderNotFoundException;
import com.nospace.repository.FileRepository;
import com.nospace.repository.FolderRepository;
import com.nospace.entities.Folder;
import com.nospace.entities.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.file.*;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@Slf4j
@RequiredArgsConstructor
public class FolderService {

    private final FolderRepository folderRepository;
    private final FileRepository fileRepository;
    private final UserService userService;

    @Value("${drive.base-path}")
    private String BASE_PATH;

    @Value("${drive.zip-temporal-container}")
    private String ZIP_TEMPORAL_CONTAINER;

    private String idGenerator(){
        return UUID.randomUUID().toString().replaceAll("-", "")
            .substring(0, 17);
    }

    public void saveRootFolder(User owner){
        Folder rootFolder = new Folder(idGenerator(), owner);
        folderRepository.save(rootFolder);
        this.saveFolderToDisk(rootFolder.getFullRoute());
    }

    @Async
    public void saveFolderToDisk(String fullRoute){
        Path newPhysicalFolder = Paths.get(BASE_PATH, fullRoute);
        try{
            Files.createDirectory(newPhysicalFolder);
        }catch (IOException e){
            throw new IllegalArgumentException("could not create new physical folder at "+fullRoute);
        }
    }

    public Folder findById(String folderId){
        return folderRepository.findById(folderId)
            .orElseThrow(() -> new IllegalArgumentException("No folder was found under the id "+folderId));
    }

    public Folder findByDepthAndNameAndOwner(long depth, String name, String username){
        User folderOwner = userService.getUserByUsername(username);

        return folderRepository.findByDepthAndFolderNameAndOwner(depth, name, folderOwner)
            .orElseThrow(() -> {
                String message = String.format("We could not find any folder with the given parameters " +
                "depth: %s, name: %s, owner-id: %s", depth, name, folderOwner.getId());

                throw new IllegalArgumentException(message);
            });
    }

    public Folder createNewFolder(Folder baseFolder, String newFolderName){
        Folder newFolder = new Folder(idGenerator(), newFolderName, baseFolder);
        saveFolderToDisk(newFolder.getFullRoute());
        return folderRepository.save(newFolder);
    }

    public void deleteFolder(String folderId){
        Folder folderToDelete = this.findById(folderId);
        deleteFromDatabase(folderToDelete);
        deletePhysicalFolder(folderToDelete.getFullRoute());
    }

    private void deleteFromDatabase(Folder folderToDelete){
        folderRepository.delete(folderToDelete);
    }

    public FolderDto renameFolder(String folderId, String newFolderName){
        Folder folder = folderRepository.findById(folderId)
            .orElseThrow(() -> new FolderNotFoundException("Could not find folder with id => "+ folderId));

        updateFilePath(folder.getFiles(), folder.getFolderName(), newFolderName);

        folder.setFolderName(newFolderName);
        folder.setSubFolders(new ArrayList<>(0));
        folder.setFiles(new ArrayList<>(0));

        String newFolderRoute = folder.getFullRoute().replaceAll("/.*/$",
            String.format("/%s/", newFolderName));
        moveFolder(folder.getFullRoute(), newFolderRoute);

        folder.setFullRoute(newFolderRoute);
        folderRepository.save(folder);
        return FolderMapperImpl.mapper.folderToFolderDto(folder);
    }

    @Async
    private void moveFolder(String oldRoute, String newRoute){
        Path source = Paths.get(BASE_PATH, oldRoute);
        Path destination = Paths.get(BASE_PATH, newRoute);
        try{
            Files.move(source, destination);
        }catch (IOException e){
            e.printStackTrace();
        }
    }

    @Async
    private void updateFilePath(List<File> files, String oldName, String newName){
        files.stream()
            .map(file -> {
                String newRoute = file.getFullRoute()
                    .replace(oldName, newName);
                file.setFullRoute(newRoute);
                return file;
            })
            .forEach(fileRepository::save);
    }

    @Async
    private void deletePhysicalFolder(String folderToDelete){
        Path folder = Paths.get(BASE_PATH, folderToDelete);
        try{
            FileUtils.deleteDirectory(folder.toFile());
        }catch (IOException e){
            e.printStackTrace();
        }
    }

    @Async
    public byte[] zipFolderContents(String folderId) throws IOException{
        String zipFilename = UUID.randomUUID().toString().toString() + ".zip";
        Folder folder = folderRepository.findById(folderId)
            .orElseThrow(() -> new IllegalArgumentException("No folder was found with id" + folderId));

        Path zipDestination = Paths.get(ZIP_TEMPORAL_CONTAINER, zipFilename);
        Path folderContents = Paths.get(BASE_PATH, folder.getFullRoute());
        ZipOutputStream outputStream = new ZipOutputStream(Files.newOutputStream(zipDestination));

        Files.walk(folderContents)
            .filter(path -> !Files.isDirectory(path))
            .forEach(path -> {
                String filename = path.toFile().getName();
                ZipEntry entry = new ZipEntry(filename);
                try{
                    outputStream.putNextEntry(entry);
                    Files.copy(path, outputStream);
                    outputStream.closeEntry();
                }catch (IOException e){
                    log.info("Could not create zip entry for file " + filename);
                    e.printStackTrace();
                }
            });

        outputStream.close();
        byte[] zipFile = Files.readAllBytes(Paths.get(ZIP_TEMPORAL_CONTAINER, zipFilename));
        deleteCompressedFile(Paths.get(ZIP_TEMPORAL_CONTAINER, zipFilename));
        return zipFile;
    }

    private void deleteCompressedFile(Path compressedFileLocation){
        try{
            Files.deleteIfExists(compressedFileLocation);
        }catch (IOException e){
            log.info("Could not delete file on location " + compressedFileLocation);
            e.printStackTrace();
        }
    }

}
