package com.zzz.call.message;

import lombok.Getter;

@Getter
public class RaftMessage{

    private final Integer term;


    public RaftMessage(int term) {
        this.term = term;
    }

}
