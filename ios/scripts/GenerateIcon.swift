import Foundation
import CoreGraphics
import ImageIO

// Deterministic, offline vector artwork. Run from any directory with `swift GenerateIcon.swift`.
let root = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
let folder = root.appendingPathComponent("StudyAlarm/Resources")
try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
let green = CGColor(red: 0.12, green: 0.42, blue: 0.36, alpha: 1)
let paper = CGColor(red: 0.97, green: 0.97, blue: 0.90, alpha: 1)
let mint = CGColor(red: 0.80, green: 0.91, blue: 0.79, alpha: 1)

for (name, size) in [("AppIcon60x60@2x", 120), ("AppIcon60x60@3x", 180),
                     ("AppIcon76x76@2x", 152), ("AppIcon83.5x83.5@2x", 167)] {
    guard let context = CGContext(data: nil, width: size, height: size,
                                  bitsPerComponent: 8, bytesPerRow: 0,
                                  space: CGColorSpaceCreateDeviceRGB(),
                                  bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue) else {
        fatalError("Cannot allocate icon context")
    }
    context.scaleBy(x: CGFloat(size) / 1024, y: CGFloat(size) / 1024)
    context.setFillColor(green)
    context.fill(CGRect(x: 0, y: 0, width: 1024, height: 1024))
    let left = CGMutablePath()
    left.move(to: CGPoint(x: 200, y: 720))
    left.addCurve(to: CGPoint(x: 500, y: 668),
                  control1: CGPoint(x: 315, y: 753), control2: CGPoint(x: 414, y: 740))
    left.addLine(to: CGPoint(x: 500, y: 286))
    left.addCurve(to: CGPoint(x: 200, y: 338),
                  control1: CGPoint(x: 402, y: 359), control2: CGPoint(x: 306, y: 367))
    left.closeSubpath()
    context.addPath(left)
    context.setFillColor(paper)
    context.fillPath()
    let right = CGMutablePath()
    right.move(to: CGPoint(x: 524, y: 668))
    right.addCurve(to: CGPoint(x: 824, y: 720),
                   control1: CGPoint(x: 610, y: 740), control2: CGPoint(x: 710, y: 753))
    right.addLine(to: CGPoint(x: 824, y: 338))
    right.addCurve(to: CGPoint(x: 524, y: 286),
                   control1: CGPoint(x: 718, y: 367), control2: CGPoint(x: 622, y: 359))
    right.closeSubpath()
    context.addPath(right)
    context.setFillColor(mint)
    context.fillPath()
    context.setStrokeColor(green)
    context.setLineWidth(18)
    context.setLineCap(.round)
    for y in [480.0, 550.0, 620.0] {
        context.move(to: CGPoint(x: 262, y: y))
        context.addLine(to: CGPoint(x: 420, y: y - 12))
        context.strokePath()
    }
    context.setFillColor(green)
    context.fillEllipse(in: CGRect(x: 580, y: 172, width: 330, height: 330))
    context.setFillColor(paper)
    context.fillEllipse(in: CGRect(x: 605, y: 197, width: 280, height: 280))
    context.setStrokeColor(green)
    context.setLineWidth(24)
    context.move(to: CGPoint(x: 745, y: 420))
    context.addLine(to: CGPoint(x: 745, y: 337))
    context.addLine(to: CGPoint(x: 690, y: 302))
    context.strokePath()
    guard let image = context.makeImage(),
          let destination = CGImageDestinationCreateWithURL(
            folder.appendingPathComponent(name + ".png") as CFURL, "public.png" as CFString, 1, nil) else {
        fatalError("Cannot create icon output")
    }
    CGImageDestinationAddImage(destination, image, nil)
    guard CGImageDestinationFinalize(destination) else { fatalError("Cannot write icon") }
}
print("Generated four iPhone / iPad icons in \(folder.path)")
