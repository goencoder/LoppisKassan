package se.goencoder.loppiskassan.records;

import org.junit.jupiter.api.Test;
import se.goencoder.loppiskassan.SoldItem;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FormatHelperTest {

    @Test
    void skipsMalformedLineWithTooFewColumns() {
        String csv = FormatHelper.CVS_HEADERS + FormatHelper.LINE_ENDING +
                "p1,i1,2024-01-01 10:00,1,100";
        List<SoldItem> items = FormatHelper.toItems(csv, true);
        assertTrue(items.isEmpty(), "Malformed line should be skipped");
    }

    @Test
    void skipsInvalidPaymentMethod() {
        String line = String.join(",", "p1", "i1", "2024-01-01 10:00", "1", "100", "Nej", "INVALID", "false");
        String csv = FormatHelper.CVS_HEADERS + FormatHelper.LINE_ENDING + line;
        List<SoldItem> items = FormatHelper.toItems(csv, true);
        assertTrue(items.isEmpty(), "Line with invalid payment method should be skipped");
    }

    @Test
    void skipsInvalidUploadedFlag() {
        String line = String.join(",", "p1", "i1", "2024-01-01 10:00", "1", "100", "Nej", "Swish", "notabool");
        String csv = FormatHelper.CVS_HEADERS + FormatHelper.LINE_ENDING + line;
        List<SoldItem> items = FormatHelper.toItems(csv, true);
        assertTrue(items.isEmpty(), "Line with invalid uploaded flag should be skipped");
    }
}

