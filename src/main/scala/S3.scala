import software.amazon.awssdk.services.s3.{S3AsyncClient, S3Client, S3Configuration}
import software.amazon.awssdk.services.s3.model.{CompleteMultipartUploadRequest, CompleteMultipartUploadResponse, CompletedMultipartUpload, CompletedPart, CreateMultipartUploadRequest, DeleteObjectResponse, PutObjectRequest, PutObjectResponse, UploadPartRequest, UploadPartResponse}
import software.amazon.awssdk.core.async.AsyncRequestBody
import cats.effect._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.multipart.{Multipart, Part}
import fs2.Stream
import fs2.interop.reactivestreams._
import fs2._
import fs2.io.file.Files

import scala.concurrent.ExecutionContext.Implicits.global
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest

import java.nio.file.Paths
import java.net.URI
import java.nio.ByteBuffer
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import cats.effect.unsafe.implicits.global
import org.reactivestreams.Publisher

import scala.jdk.CollectionConverters._

object S3 {
  val region = Region.US_EAST_1
  val bucketName = "images-dev"

  val s3Async: S3AsyncClient = S3AsyncClient.builder()
    .region(region)
    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("8imME71Gy6SGhAuSCbH2", "CMVQSD15OxFgfTYGZNtugLXuZYFHEGOWjPLOxGxM")))
    .endpointOverride(URI.create("http://localhost:5053"))
    .serviceConfiguration(S3Configuration.builder()
      .pathStyleAccessEnabled(true) // Enable path-style access
      .build())
    .build()
  def uploadFile(filename: String, filePart:Part[IO]): IO[CompleteMultipartUploadResponse] = {
    val createMultipartUploadRequest = CreateMultipartUploadRequest.builder()
      .bucket(bucketName)
      .key(filename)
      .build()

    for {
      createMultipartUploadResponse <- IO.fromCompletableFuture(IO(s3Async.createMultipartUpload(createMultipartUploadRequest).toCompletableFuture))
      uploadId = createMultipartUploadResponse.uploadId()

      partResponses <- filePart.body.chunkN(10 * 1024 * 1024)
        .zipWithIndex // To keep track of part numbers
        .evalMap { case (chunk, partNumber) =>
          uploadPart(bucketName, filename, partNumber.toInt + 1, uploadId, chunk.toByteBuffer)
            .map(response => (partNumber.toInt + 1, response.eTag())) // Pairing part number with ETag
        }
        .compile
        .toList

      // Complete multipart upload
      completed <- IO.fromCompletableFuture(IO {
        s3Async.completeMultipartUpload(CompleteMultipartUploadRequest.builder()
          .bucket(bucketName)
          .key(filename)
          .uploadId(uploadId)
          .multipartUpload(CompletedMultipartUpload.builder()
            .parts(partResponses.map { case (partNumber, eTag) =>
              CompletedPart.builder().partNumber(partNumber).eTag(eTag).build() // Constructing CompletedPart instances
            }.asJava)
            .build())
          .build())
          .toCompletableFuture
      })
    } yield completed
  }

  def deleteFile(objectKey: String):IO[DeleteObjectResponse] = {
    val deleteObjectRequest = DeleteObjectRequest.builder()
      .bucket(bucketName)
      .key(objectKey)
      .build()

    IO.fromCompletableFuture(IO(s3Async.deleteObject(deleteObjectRequest)));
  }

  private def uploadPart(bucket: String, key: String, partNumber: Int, uploadId: String, byteBuffer: ByteBuffer): IO[UploadPartResponse] = {
    val requestBody = AsyncRequestBody.fromByteBuffer(byteBuffer)

    val uploadPartRequest = UploadPartRequest.builder()
      .bucket(bucket)
      .key(key)
      .partNumber(partNumber)
      .uploadId(uploadId)
      .build()

    IO.fromCompletableFuture(IO(s3Async.uploadPart(uploadPartRequest, requestBody).toCompletableFuture))
  }
}
