package com.example.demo.config;

import com.example.demo.jobs.domain.Keyword;
import com.example.demo.jobs.domain.KeywordUrl;
import com.example.demo.util.S3Uploader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JpaItemWriter;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.persistence.EntityManagerFactory;
import java.io.FileOutputStream;

@Slf4j
@RequiredArgsConstructor
@Configuration
@EnableBatchProcessing
public class SearchingImageConvertJobConfiguration {

    private static final String SEARCH_NAVER_URL = "https://search.naver.com/search.naver?query=";
    private static final String NEWS_QUERY = "&where=news&ie=utf8&sm=nws_hty";

    private static final String BASE_S3_PATH = "https://s3.us-east-2.amazonaws.com/demosearchingimage/";

    public static final String JOB_NAME = "SearchingImageConvertBatch";
    public static final String BEAN_PREFIX = JOB_NAME + "_";

    private final JobBuilderFactory jobBuilderFactory;
    private final StepBuilderFactory stepBuilderFactory;
    private final EntityManagerFactory emf;

    private final S3Uploader s3Uploader;

    @Value("${chunkSize:1000}")
    private int chunkSize;

    @Bean(JOB_NAME)
    public Job job() {
        return jobBuilderFactory.get(JOB_NAME)
                .preventRestart()
                .start(step())
                .build();
    }

    @Bean(BEAN_PREFIX + "step")
    @JobScope
    public Step step() {
        return stepBuilderFactory.get(BEAN_PREFIX + "step")
                .<Keyword, KeywordUrl>chunk(chunkSize)
                .reader(reader())
                .processor(processor())
                .writer(jpaItemWriter())
                .build();
    }

    @Bean
    public JpaPagingItemReader<Keyword> reader() {
        return new JpaPagingItemReaderBuilder<Keyword>()
                .name(BEAN_PREFIX + "reader")
                .entityManagerFactory(emf)
                .pageSize(chunkSize)
                .queryString("SELECT k FROM Keyword k")
                .build();
    }

    @Bean
    public ItemProcessor<Keyword, KeywordUrl> processor() {
        return keyword -> {
            // 크롬 드라이버의 경로를 설정
            System.setProperty("webdriver.chrome.driver", System.getProperty("user.dir") + "/drivers/chromedriver");

            String url = keyword.getName() + System.currentTimeMillis();
            log.info("Keyword url = {}", url);

            // 해당 검색어로 크롤링 후 스크린샷으로 변환
            WebDriver driver = new ChromeDriver();
            driver.get(SEARCH_NAVER_URL + keyword.getName() + NEWS_QUERY);


            // 스크린샷
            TakesScreenshot screenshot = (TakesScreenshot) driver;
            byte[] imageByte = screenshot.getScreenshotAs(OutputType.BYTES);
            try (FileOutputStream fos = new FileOutputStream(System.getProperty("user.home") + "/screenshot/" + url + ".png")) {
                fos.write(imageByte);
                fos.close();
            }

            // 드라이버 종료
            driver.quit();

            s3Uploader.uploadFile(url, imageByte);
            String s3Url = BASE_S3_PATH + url;
            KeywordUrl keywordUrl = new KeywordUrl(keyword.getId(), s3Url);

            return keywordUrl;
        };
    }

    private ItemWriter<KeywordUrl> writer() {
        return items -> {
            for (KeywordUrl item : items) {
                log.info("KeywordUrl = {}", item);
            }
        };
    }

    @Bean
    public JpaItemWriter<KeywordUrl> jpaItemWriter() {
        JpaItemWriter<KeywordUrl> jpaItemWriter = new JpaItemWriter<>();
        jpaItemWriter.setEntityManagerFactory(emf);
        return jpaItemWriter;
    }
}