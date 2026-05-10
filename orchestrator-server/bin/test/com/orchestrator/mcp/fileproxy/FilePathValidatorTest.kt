package com.orchestrator.mcp.fileproxy

import com.orchestrator.mcp.core.model.FileNotFoundException
import com.orchestrator.mcp.core.model.InvalidFilePathException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Files
import java.nio.file.Path

/**
 * Unit tests for FilePathValidator — path security validation.
 * STC: UT-08 — Path traversal detection
 * STC: UT-05 — File not found
 */
class FilePathValidatorTest : DescribeSpec({

    describe("resolvePath") {
        it("returns absolute path unchanged") {
            val tempFile = Files.createTempFile("resolve-test", ".txt")
            try {
                val absPath = tempFile.toAbsolutePath().toString()
                FilePathValidator.resolvePath(absPath) shouldBe absPath
            } finally {
                Files.deleteIfExists(tempFile)
            }
        }

        it("resolves relative path against working directory") {
            val resolved = FilePathValidator.resolvePath("documents/test.md")
            val expected = Path.of(System.getProperty("user.dir"))
                .resolve("documents/test.md").normalize().toString()
            resolved shouldBe expected
        }
    }

    describe("validateInputPath") {
        // STC: UT-08 — Path traversal detected
        it("rejects path with traversal sequences") {
            val tempDir = Files.createTempDirectory("traversal-test")
            val traversalPath = "${tempDir}/../../../etc/passwd"
            val ex = shouldThrow<InvalidFilePathException> {
                FilePathValidator.validateInputPath(traversalPath)
            }
            ex.message shouldContain "path traversal not allowed"
            Files.deleteIfExists(tempDir)
        }

        it("resolves relative path and validates existence") {
            shouldThrow<FileNotFoundException> {
                FilePathValidator.validateInputPath("nonexistent_xyz_12345.pdf")
            }
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

        it("resolves relative path and creates parent directories") {
            val tempDir = Files.createTempDirectory("out-relative")
            val relativePath = "${tempDir}/new_subdir/output.pdf"
            FilePathValidator.validateOutputPath(relativePath)
            val parent = Path.of(relativePath).parent
            Files.exists(parent) shouldBe true
            // Cleanup
            Files.deleteIfExists(parent)
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
