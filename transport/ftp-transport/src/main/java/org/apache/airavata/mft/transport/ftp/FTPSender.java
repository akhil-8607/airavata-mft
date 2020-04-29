package org.apache.airavata.mft.transport.ftp;

import org.apache.airavata.mft.core.ConnectorContext;
import org.apache.airavata.mft.core.api.Connector;
import org.apache.airavata.mft.resource.client.ResourceServiceClient;
import org.apache.airavata.mft.resource.service.FTPResource;
import org.apache.airavata.mft.resource.service.FTPResourceGetRequest;
import org.apache.airavata.mft.resource.service.ResourceServiceGrpc;
import org.apache.airavata.mft.secret.client.SecretServiceClient;
import org.apache.airavata.mft.secret.service.FTPSecret;
import org.apache.airavata.mft.secret.service.FTPSecretGetRequest;
import org.apache.airavata.mft.secret.service.SecretServiceGrpc;
import org.apache.commons.net.ftp.FTPClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;

public class FTPSender implements Connector {

    private static final Logger logger = LoggerFactory.getLogger(FTPReceiver.class);

    private FTPResource resource;
    private boolean initialized;
    private FTPClient ftpClient;

    @Override
    public void init(String resourceId, String credentialToken, String resourceServiceHost, int resourceServicePort, String secretServiceHost, int secretServicePort) throws Exception {
        this.initialized = true;

        ResourceServiceGrpc.ResourceServiceBlockingStub resourceClient = ResourceServiceClient.buildClient(resourceServiceHost, resourceServicePort);
        this.resource = resourceClient.getFTPResource(FTPResourceGetRequest.newBuilder().setResourceId(resourceId).build());

        SecretServiceGrpc.SecretServiceBlockingStub secretClient = SecretServiceClient.buildClient(secretServiceHost, secretServicePort);
        FTPSecret ftpSecret = secretClient.getFTPSecret(FTPSecretGetRequest.newBuilder().setSecretId(credentialToken).build());

        this.ftpClient = FTPTransportUtil.getFTPClient(this.resource, ftpSecret);
    }

    @Override
    public void destroy() {
        FTPTransportUtil.disconnectFTP(ftpClient);
    }

    @Override
    public void startStream(ConnectorContext context) throws Exception {

        logger.info("Starting FTP sender stream for transfer {}", context.getTransferId());

        checkInitialized();
        InputStream in = context.getStreamBuffer().getInputStream();
        long fileSize = context.getMetadata().getResourceSize();
        OutputStream outputStream = ftpClient.storeFileStream(resource.getResourcePath());

        byte[] buf = new byte[1024];
        while (true) {
            int bufSize;

            if (buf.length < fileSize) {
                bufSize = buf.length;
            } else {
                bufSize = (int) fileSize;
            }
            bufSize = in.read(buf, 0, bufSize);

            if (bufSize < 0) {
                break;
            }

            outputStream.write(buf, 0, bufSize);
            outputStream.flush();

            fileSize -= bufSize;
            if (fileSize == 0L)
                break;
        }

        in.close();
        outputStream.close();

        logger.info("Completed FTP sender stream for transfer {}", context.getTransferId());

    }

    private void checkInitialized() {
        if (!initialized) {
            throw new IllegalStateException("FTP Sender is not initialized");
        }
    }
}