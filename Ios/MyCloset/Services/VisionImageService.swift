import CoreImage
import ImageIO
import UIKit
import Vision

protocol VisionImageServicing: Sendable {
    func removeBackground(from image: UIImage) async throws -> UIImage
}

protocol VisionImageOperation: Sendable {
    associatedtype Output: Sendable

    nonisolated func perform(with requestHandler: VNImageRequestHandler) throws -> Output
}

enum VisionImageServiceError: LocalizedError {
    case missingCGImage
    case foregroundMaskUnavailable
    case renderedImageUnavailable

    var errorDescription: String? {
        switch self {
        case .missingCGImage:
            return "UIImage에서 CGImage를 만들 수 없습니다."
        case .foregroundMaskUnavailable:
            return "이미지에서 분리 가능한 전경을 찾지 못했습니다."
        case .renderedImageUnavailable:
            return "투명 배경 이미지를 렌더링하지 못했습니다."
        }
    }
}

@available(iOS 17.0, *)
actor VisionImageService: VisionImageServicing {
    static let shared = VisionImageService()

    private let context: CIContext

    init(context: CIContext = CIContext(options: nil)) {
        self.context = context
    }

    func removeBackground(from image: UIImage) async throws -> UIImage {
        let operation = RemoveBackgroundOperation(image: image, context: context)
        return try await withCheckedThrowingContinuation { continuation in
            DispatchQueue.global(qos: .userInitiated).async {
                do {
                    let result = try self.perform(operation, for: image)
                    continuation.resume(returning: result)
                } catch {
                    continuation.resume(throwing: error)
                }
            }
        }
    }

    nonisolated func perform<Operation: VisionImageOperation>(
        _ operation: Operation,
        for image: UIImage
    ) throws -> Operation.Output {
        guard let cgImage = image.cgImage else {
            throw VisionImageServiceError.missingCGImage
        }

        let requestHandler = VNImageRequestHandler(
            cgImage: cgImage,
            orientation: CGImagePropertyOrientation(image.imageOrientation),
            options: [:]
        )

        return try operation.perform(with: requestHandler)
    }
}

@available(iOS 17.0, *)
private struct RemoveBackgroundOperation: VisionImageOperation {
    let image: UIImage
    let context: CIContext

    nonisolated func perform(with requestHandler: VNImageRequestHandler) throws -> UIImage {
        let request = VNGenerateForegroundInstanceMaskRequest()
        try requestHandler.perform([request])

        guard let observation = request.results?.first else {
            throw VisionImageServiceError.foregroundMaskUnavailable
        }

        let maskedBuffer = try observation.generateMaskedImage(
            ofInstances: observation.allInstances,
            from: requestHandler,
            croppedToInstancesExtent: false
        )

        let outputImage = CIImage(cvPixelBuffer: maskedBuffer)
        guard let cgImage = context.createCGImage(outputImage, from: outputImage.extent) else {
            throw VisionImageServiceError.renderedImageUnavailable
        }

        return UIImage(cgImage: cgImage, scale: image.scale, orientation: .up)
    }
}

private extension CGImagePropertyOrientation {
    nonisolated init(_ orientation: UIImage.Orientation) {
        switch orientation {
        case .up:
            self = .up
        case .upMirrored:
            self = .upMirrored
        case .down:
            self = .down
        case .downMirrored:
            self = .downMirrored
        case .left:
            self = .left
        case .leftMirrored:
            self = .leftMirrored
        case .right:
            self = .right
        case .rightMirrored:
            self = .rightMirrored
        @unknown default:
            self = .up
        }
    }
}
