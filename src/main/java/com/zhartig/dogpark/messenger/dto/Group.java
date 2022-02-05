package com.zhartig.dogpark.messenger.dto;

import com.opencsv.bean.CsvBindAndSplitByName;
import com.opencsv.bean.CsvBindByName;
import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Group {
    @CsvBindByName(column = "Name")
    private String name;
    @CsvBindByName(column = "Owner")
    private String owner;
    @CsvBindAndSplitByName(column = "Members", elementType = String.class)
    private List<String> members;
}
