package com.nospace.entities;

import com.fasterxml.jackson.annotation.JsonBackReference;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "files")
@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class File {

    @Id
    private String id;

    @Column(name = "full_route", nullable = false)
    private String fullRoute;

    @Column(name = "filename", nullable = false)
    private String filename;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "uploadedAt", nullable = false)
    private LocalDate uploadedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "containing_folder_id", nullable = false)
    @JsonBackReference(value = "folder-files")
    private Folder containingFolder;

}
