// package com.quicksand.bigdata.metric.stitcher.dynamic;
//
// import com.netflix.graphql.dgs.DgsComponent;
// import com.netflix.graphql.dgs.DgsQuery;
// import lombok.extern.slf4j.Slf4j;
//
// import java.util.concurrent.CompletableFuture;
// import java.util.concurrent.TimeUnit;
//
// /**
//  * ThreadOptiimizedFetcher
//  *
//  * @author page
//  * @date 2023/1/4
//  */
// @Slf4j
// @DgsComponent
// public class ThreadOptiimizedFetcher {
//
//     @DgsQuery
//     public CompletableFuture<String> multipleMetricCandidateDispatcherx() {
//         return CompletableFuture.supplyAsync(() -> {
//             try {
//                 TimeUnit.SECONDS.sleep(5L);
//             } catch (InterruptedException e) {
//                 e.printStackTrace();
//             }
//             return "Hello,DgsData! CompletableFutureX";
//         });
//     }
//
//     @DgsQuery
//     public CompletableFuture<String> multipleMetricCandidateDispatchery() {
//         return CompletableFuture.supplyAsync(() -> {
//             try {
//                 TimeUnit.SECONDS.sleep(5L);
//             } catch (InterruptedException e) {
//                 e.printStackTrace();
//             }
//             return "Hello,DgsData! CompletableFutureY";
//         });
//     }
//
// }
