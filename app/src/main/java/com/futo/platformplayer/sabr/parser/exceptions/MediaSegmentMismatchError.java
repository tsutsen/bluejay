package com.futo.platformplayer.sabr.parser.exceptions;

import com.futo.platformplayer.sabr.protos.videostreaming.FormatId;

public class MediaSegmentMismatchError extends SabrStreamError {
    public final long expectedSequenceNumber;
    public final long receivedSequenceNumber;

    public MediaSegmentMismatchError(FormatId formatId, long expectedSequenceNumber, long receivedSequenceNumber) {
        super(String.format(
                "Segment sequence number mismatch for format %s: expected %s, received %s",
                formatId,
                expectedSequenceNumber,
                receivedSequenceNumber
        ));
        this.receivedSequenceNumber = receivedSequenceNumber;
        this.expectedSequenceNumber = expectedSequenceNumber;
    }
}
