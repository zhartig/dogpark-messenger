package com.zhartig.dogpark.messenger.dto;

import com.opencsv.bean.CsvBindAndSplitByName;
import com.opencsv.bean.CsvBindByName;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter @Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Patron {
    @CsvBindByName(column = "Owner")
    private String ownerName;
    @CsvBindByName(column = "Dog")
    private String dogName;
    @CsvBindByName(column = "Phone")
    private String phoneNumber;
    @CsvBindAndSplitByName(column = "blocked", elementType = String.class)
    private List<String> blockedList;
}
