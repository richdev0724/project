package com.marketmono.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketmono.dto.CongressMemberDetailsDto;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Loads representatives.json at startup and serves congress member details
 * with constructed image URLs.
 *
 * Image sources:
 *   House:  https://clerk.house.gov/content/assets/img/members/{imageFile}
 *   Senate: https://www.senate.gov/img/member/{imageFile}
 */
@Service
@Slf4j
public class CongressMemberDetailsService {

    private static final String HOUSE_IMAGE_BASE = "https://clerk.house.gov/content/assets/img/members/";
    private static final String SENATE_IMAGE_BASE = "https://www.senate.gov/img/member/";

    private List<CongressMemberDetailsDto> members = Collections.emptyList();

    @PostConstruct
    void loadData() {
        var mapper = new ObjectMapper();
        try {
            InputStream is = new ClassPathResource("representatives.json").getInputStream();
            List<Map<String, Object>> raw = mapper.readValue(is, new TypeReference<>() {});
            List<CongressMemberDetailsDto> result = new ArrayList<>(raw.size());

            for (Map<String, Object> entry : raw) {
                boolean isSenator = entry.containsKey("role")
                        && "Senator".equalsIgnoreCase((String) entry.get("role"));

                String imageFile = (String) entry.get("imageFile");
                String imageUrl = null;
                if (imageFile != null && !imageFile.isBlank()) {
                    imageUrl = isSenator
                            ? SENATE_IMAGE_BASE + imageFile
                            : HOUSE_IMAGE_BASE + imageFile;
                }

                Integer age = entry.get("age") instanceof Number n ? n.intValue() : null;

                result.add(new CongressMemberDetailsDto(
                        str(entry, "id"),
                        str(entry, "bioguideId"),
                        str(entry, "firstName"),
                        str(entry, "lastName"),
                        str(entry, "middleName"),
                        str(entry, "suffix"),
                        str(entry, "officialName"),
                        str(entry, "party"),
                        str(entry, "state"),
                        str(entry, "stateCode"),
                        isSenator ? "Senate" : "House",
                        str(entry, "district"),
                        str(entry, "town"),
                        str(entry, "phone"),
                        str(entry, "officeBuilding"),
                        str(entry, "officeRoom"),
                        str(entry, "website"),
                        age,
                        imageUrl
                ));
            }

            this.members = Collections.unmodifiableList(result);
            log.info("Loaded {} congress member details ({} House, {} Senate)",
                    members.size(),
                    members.stream().filter(m -> "House".equals(m.chamber())).count(),
                    members.stream().filter(m -> "Senate".equals(m.chamber())).count());
        } catch (IOException e) {
            log.error("Failed to load representatives.json", e);
        }
    }

    public List<CongressMemberDetailsDto> getAllMembers() {
        return members;
    }

    /**
     * Search by name (case-insensitive partial match on officialName, firstName, or lastName)
     */
    public List<CongressMemberDetailsDto> searchByName(String name) {
        String lower = name.toLowerCase();
        return members.stream()
                .filter(m -> contains(m.officialName(), lower)
                        || contains(m.firstName(), lower)
                        || contains(m.lastName(), lower))
                .toList();
    }

    /**
     * Find by bioguideId or senator id
     */
    public CongressMemberDetailsDto findById(String id) {
        return members.stream()
                .filter(m -> id.equalsIgnoreCase(m.bioguideId()) || id.equalsIgnoreCase(m.id()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Filter by state code (e.g. "CA", "TX")
     */
    public List<CongressMemberDetailsDto> findByState(String stateCode) {
        String upper = stateCode.toUpperCase();
        return members.stream()
                .filter(m -> upper.equals(m.stateCode())
                        || (m.state() != null && m.state().equalsIgnoreCase(stateCode)))
                .toList();
    }

    /**
     * Filter by chamber: "House" or "Senate"
     */
    public List<CongressMemberDetailsDto> findByChamber(String chamber) {
        return members.stream()
                .filter(m -> chamber.equalsIgnoreCase(m.chamber()))
                .toList();
    }

    private static String str(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val instanceof String s && !s.isBlank() ? s : null;
    }

    private static boolean contains(String value, String search) {
        return value != null && value.toLowerCase().contains(search);
    }
}
