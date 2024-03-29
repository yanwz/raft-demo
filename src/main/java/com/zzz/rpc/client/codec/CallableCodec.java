package com.zzz.rpc.client.codec;

import com.zzz.rpc.client.CallableRaftReq;
import com.zzz.rpc.message.Request;
import com.zzz.rpc.message.Response;
import com.zzz.rpc.client.exception.ErrorCodeException;
import com.zzz.rpc.message.res.RaftRsp;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.concurrent.Promise;

import java.nio.channels.ClosedChannelException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class CallableCodec extends ChannelDuplexHandler {

    private final Map<String, Promise<RaftRsp>> promiseContext;


    public CallableCodec() {
        this.promiseContext = new HashMap<>();
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof CallableRaftReq) {
            CallableRaftReq callableRaftReq = (CallableRaftReq) msg;
            Promise<?> callPromise = callableRaftReq.getPromise();
            boolean oneway = callableRaftReq.isOneway();
            String id = generateId();
            msg = new Request(id, callableRaftReq.getRaftReq());
            if(!oneway){
                promiseContext.put(id, (Promise<RaftRsp>)callPromise);
                promise.addListener(future -> promiseContext.remove(id));
            }
            ChannelFuture future = ctx.write(msg, promise.unvoid());
            future.addListener(f -> {
                if(f.isSuccess()){
                    if(oneway){
                        callPromise.trySuccess(null);
                    }
                }else {
                    callPromise.tryFailure(f.cause());
                }
            });
            callPromise.addListener((f)->future.cancel(false));
        }else {
            ctx.write(msg, promise);
        }
    }

    private String generateId() {
        return UUID.randomUUID().toString().replaceAll("-", "");
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof Response) {
            Response response = (Response) msg;
            Promise<RaftRsp> promise = promiseContext.get(response.getId());
            if (promise != null) {
                if(response.isSuccess()){
                    promise.trySuccess(response.getContent());
                }else {
                    promise.tryFailure(new ErrorCodeException(response.getErrorCode()));
                }
            }
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Iterator<Promise<RaftRsp>> iterator = promiseContext.values().iterator();
        while (iterator.hasNext()) {
            Promise<RaftRsp> promise = iterator.next();
            promise.tryFailure(new ClosedChannelException());
            iterator.remove();
        }
        ctx.fireChannelInactive();
    }

}
