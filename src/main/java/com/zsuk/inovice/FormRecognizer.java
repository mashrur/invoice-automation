package com.zsuk.inovice;

import com.azure.ai.formrecognizer.*;

import com.azure.ai.formrecognizer.documentanalysis.models.*;
import com.azure.ai.formrecognizer.documentanalysis.DocumentAnalysisClient;
import com.azure.ai.formrecognizer.documentanalysis.DocumentAnalysisClientBuilder;

import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.util.BinaryData;
import com.azure.core.util.polling.SyncPoller;
import com.dropbox.core.DbxDownloader;
import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.ListFolderResult;
import com.dropbox.core.v2.files.Metadata;
import com.dropbox.core.v2.files.WriteMode;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.time.LocalDate;
import java.util.stream.Collectors;
import com.zsuk.config.DropboxAuthenticator;

public class FormRecognizer {

    // set `<your-endpoint>` and `<your-key>` variables with the values from the Azure portal
    private static final String endpoint = "https://uksouth.api.cognitive.microsoft.com/";
    private static final String key = "a5297f9d1d5b4555b092d76a2f0f018e";
    private static final String DROP_BOX_ACCESS_TOKEN = "sl.BlWPqm-dvUeM0gu0hGHoLDyAu69SseJwlsKedN9W-PfWNFC3SpVOXgZjj9uAlAurF_Dah86DG59H3o-u7w69ol6cT1VLKmOD-x5a5U4bffhx80y-qNBsJi3qJ6KLIkVV0niGbSmj_rSZ";

    private static final List<String> csvColumnNames = List.of("File Name", "Company Name", "Invoice Date", "Net Parts", "Net Labour", "Sub Total", "MOT Test", "Total Payable" );

    public static void process(String modelId, String sourcePath, String targetPath) throws IOException, DbxException, InterruptedException {
        DropboxAuthenticator authenticator = new DropboxAuthenticator(DROP_BOX_ACCESS_TOKEN);
        //System.out.println(authenticator.getAuthoriseUrl());
        //System.out.println(authenticator.getAccessToken(DROP_BOX_ACCESS_TOKEN));


        DbxClientV2 client = authenticator.getClient();
        List<String> csvContent = processFiles(client);
        ByteArrayInputStream inputStream = convertListToStream(csvContent);
        //client.
        ZonedDateTime londonTime = ZonedDateTime.now(ZoneId.of("Europe/London"));

        // Format the date and time
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");
        String formattedDate = londonTime.format(formatter);
        uploadToDropbox(inputStream, client, "/csvoutput/output-" + formattedDate + ".csv");

    }

    public static void uploadToDropbox(InputStream inputStream, DbxClientV2 client, String dropboxPath) {
        try {
            FileMetadata metadata = client.files().uploadBuilder(dropboxPath)
                    .withMode(WriteMode.OVERWRITE)
                    .uploadAndFinish(inputStream);
            System.out.println("Uploaded file with revision: " + metadata.getRev());
        } catch (DbxException | IOException e) {
            e.printStackTrace();
        }
    }

    public static ByteArrayInputStream convertListToStream(List<String> lines) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (Writer writer = new OutputStreamWriter(byteArrayOutputStream, StandardCharsets.UTF_8)) {
            for (String line : lines) {
                writer.write(line);
                writer.write(System.lineSeparator());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
    }

    private static Map<String, String> getInvoiceImageContents(InputStream is, long length) {
        Map<String, String> data = new HashMap<>();
        DocumentAnalysisClient client = new DocumentAnalysisClientBuilder()
                .credential(new AzureKeyCredential(key))
                .endpoint(endpoint)
                .buildClient();

        String modelId = "mot-invoice";
        BinaryData documentData = BinaryData.fromStream(is, length);//BinaryData.fromFile(filePath, (int) document.length());

        SyncPoller < OperationResult, AnalyzeResult > analyzeInvoicePoller = client.beginAnalyzeDocument(modelId, documentData);

        AnalyzeResult analyzeResult = analyzeInvoicePoller.getFinalResult();
        for (int i = 0; i < analyzeResult.getDocuments().size(); i++) {
            final AnalyzedDocument analyzedDocument = analyzeResult.getDocuments().get(i);
            System.out.printf("----------- Analyzing custom document %d -----------%n", i);
            System.out.printf("Analyzed document has doc type %s with confidence : %.2f%n",
                    analyzedDocument.getDocType(), analyzedDocument.getConfidence());
            analyzedDocument.getFields().forEach((key, documentField) -> {
                if (documentField.getContent() != null) {
                    System.out.println(key + " : " + documentField.getContent() + " (" + documentField.getConfidence() + ")");
                    data.put(key, documentField.getContent());
                }
            });
        }
        return data;

    }


    private static DbxClientV2 getDropBoxClient() {
        DbxRequestConfig config = DbxRequestConfig.newBuilder("dropbox/java-tutorial").build();
        DbxClientV2 client = new DbxClientV2(config, DROP_BOX_ACCESS_TOKEN);
        return client;
    }

    public static String getCsvLine(List<String> keys, Map<String, String> map) {
        StringBuilder result = new StringBuilder();

        for (String key : keys) {
            if (map.containsKey(key)) {
                result.append(map.get(key));
            }
            result.append(",");
        }

        return result.toString();
    }

    private static List<String> processFiles(DbxClientV2 client) throws DbxException, IOException, InterruptedException {
        // Get files and folder metadata from Dropbox root directory

        ListFolderResult result = client.files().listFolder("");
        List<String> csvContent = new ArrayList<>();
        csvContent.add(String.join(", ", csvColumnNames));
        while (true) {
            for (Metadata metadata : result.getEntries()) {
                if (metadata instanceof FileMetadata) {
                    InputStream in = null;
                    try {
                        // Get file from Dropbox
                        in = client.files().download(metadata.getPathLower()).getInputStream();
                        System.out.println(metadata.getPathLower());
                        //InputStream is = client.files().download(metadata.getPathLower()).getInputStream();
                        Map<String, String> csvLineValues = getInvoiceImageContents(in, ((FileMetadata) metadata).getSize());
                        csvLineValues.put("File Name", metadata.getPathLower());
                        csvContent.add(getCsvLine(csvColumnNames, csvLineValues));
                    }
                    catch (Exception innerEx) {
                        // Handle or log the specific error for this file
                        System.err.println("Error processing file: " + metadata.getPathLower());
                        innerEx.printStackTrace();
                    }
                    finally {
                        // Close the InputStream
                        if (in != null) {
                            in.close();
                        }
                    }
                    Thread.sleep(8000);
                }

            }

            if (!result.getHasMore()) {
                return csvContent;
            }

            result = client.files().listFolderContinue(result.getCursor());
        }
    }
/*

    private static File getDocumentFile() throws URISyntaxException {
        URL resource = com.zsuk.inovice.FormRecognizer.class.getClassLoader().getResource("SALES0423-15.jpg");
        if (resource == null) {
            throw new IllegalArgumentException("file not found!");
        } else {

            // failed if files have whitespaces or special characters
            //return new File(resource.getFile());

            return new File(resource.toURI());
        }
    }*/
}