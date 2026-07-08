package com.marketmono.dto.waitlist;

import java.util.List;

public record BulkDeleteWaitlistResponse(
    int deletedCount,
    List<WaitlistedUserDto> deletedUsers
) {
}
