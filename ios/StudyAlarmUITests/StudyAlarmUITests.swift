import XCTest

final class StudyAlarmUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    @MainActor
    func testCreateEditAndDeletePlan() throws {
        let app = XCUIApplication()
        app.launchArguments = ["--ui-testing"]
        app.launch()

        let plansTab = app.tabBars.buttons["计划"]
        XCTAssertTrue(plansTab.waitForExistence(timeout: 10))
        plansTab.tap()

        let planName = app.staticTexts["工作日（不含周二）"]
        XCTAssertTrue(planName.waitForExistence(timeout: 10))

        let copyButton = app.buttons
            .matching(NSPredicate(format: "identifier BEGINSWITH 'copyPlan-'"))
            .firstMatch
        XCTAssertTrue(copyButton.waitForExistence(timeout: 5))
        copyButton.tap()
        let saveButton = app.buttons["savePlan"]
        XCTAssertTrue(saveButton.waitForExistence(timeout: 5))
        saveButton.tap()
        XCTAssertTrue(app.staticTexts["工作日（不含周二） 副本"].waitForExistence(timeout: 5))

        let editButton = app.buttons
            .matching(NSPredicate(format: "identifier BEGINSWITH 'editPlan-'"))
            .firstMatch
        XCTAssertTrue(editButton.waitForExistence(timeout: 5))
        editButton.tap()

        XCTAssertTrue(app.navigationBars["编辑计划"].waitForExistence(timeout: 5))
        let nameField = app.textFields["planName"]
        XCTAssertTrue(nameField.waitForExistence(timeout: 5))
        nameField.tap()
        nameField.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: 12))
        nameField.typeText("周末学习")
        app.buttons["savePlan"].tap()

        XCTAssertTrue(app.navigationBars["周末学习"].waitForExistence(timeout: 5))
        let deleteButton = app.buttons["deletePlan"]
        XCTAssertTrue(deleteButton.waitForExistence(timeout: 5))
        deleteButton.tap()

        let destructiveButtons = app.buttons.matching(identifier: "删除计划")
        let destructiveButtonCount = destructiveButtons.count
        XCTAssertGreaterThan(destructiveButtonCount, 0)
        let confirmationButton = destructiveButtons.element(boundBy: destructiveButtonCount - 1)
        XCTAssertTrue(confirmationButton.waitForExistence(timeout: 5))
        confirmationButton.tap()

        XCTAssertTrue(app.staticTexts["3 组"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.staticTexts["周末学习"].exists)
    }
}
