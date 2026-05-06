package com.orchestrator.mcp.fileproxy

import com.orchestrator.mcp.core.model.FileNotFoundException
import com.orchestrator.mcp.core.model.InvalidFilePathException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.string.shouldContain
import java.nio.file.Files

/**
 * Unit tests for FilePathValidator — path security validation.
 * STC: UT-08 — Path traversal detection
 * STC: UT-05 — File not found
 */
class FilePathValidatorTest : DescribeSpec({

    describe("validateInputPath") {
        // STC: UT-08 — Path traversal detected
        it("rejects path with traversal sequences") {
            // Use an absolute path with traversal for current OS
            val tempDir = Files.createTempDirectory("traversal-test")
            val traversalPath = "${tempDir}/../../../etc/passwd"
            val ex = shouldThrow<InvalidFilePathException> {
                FilePathValidator.validateInputPath(traversalPath)
            }
            ex.message shouldContain "path traversal not allowed"
            Files.deleteIfExists(tempDir)
        }

        it("rejects relative paths") {
            val ex = shouldThrow<InvalidFilePathException> {
                FilePathValidator.validateInputPath("./relative/file.pdf")
            }
            ex.message shouldContain "path must be absolute"
        }

        // STC: UT-05 — File not found
        it("rejects non-existent file") {
            val tempDir = Files.createTempDirectory("notfound-test")
            val nonExistentPath = "${tempDir}/nonexistent_file_xyz_12345.pdf"
            shouldThrow<FileNotFoundException> {
                FilePathValidator.validateInputPath(nonExistentPath)
            }
            Files.deleteIfExists(tempDir)
        }

        it("accepts valid absolute path to existing file") {
            val tempFile = Files.createTempFile("test-validator", ".txt")
            try {
                FilePathValidator.validateInputPath(tempFile.toString())
            } finally {
                Files.deleteIfExists(tempFile)
            }
        }
    }

    describe("validateOutputPath") {
        it("rejects path with traversal sequences") {
            val tempDir = Files.createTempDirectory("out-traversal")
            val traversalPath = "${tempDir}/../../../etc/output.pdf"
            val ex = shouldThrow<InvalidFilePathException> {
                FilePathValidator.validateOutputPath(traversalPath)
            }
            ex.message shouldContain "path traversal not allowed"
            Files.deleteIfExists(tempDir)
        }

        it("rejects relative output paths") {
            val ex = shouldThrow<InvalidFilePathException> {
                FilePathValidator.validateOutputPath("relative/output.pdf")
            }
            ex.message shouldContain "path must be absolute"
        }

        it("rejects output path with non-existent parent directory") {
            val tempDir = Files.createTempDirectory("out-nodir")
            val badPath = "${tempDir}/nonexistent_subdir/output.pdf"
            val ex = shouldThrow<InvalidFilePathException> {
                FilePathValidator.validateOutputPath(badPath)
            }
            ex.message shouldContain "does not exist"
            Files.deleteIfExists(tempDir)
        }

        it("accepts valid output path with existing parent directory") {
            val tempDir = Files.createTempDirectory("test-output")
            try {
                FilePathValidator.validateOutputPath("${tempDir}/output.pdf")
            } finally {
                Files.deleteIfExists(tempDir)
            }
        }
    }
})
