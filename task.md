your task is to fix the test `testGtsFormat` in kotlin/com/emberjs/gts/GtsFormatIdentTest.kt.

kotlin/com/emberjs/gts/GtsSupport.kt has the implementation. starting from line 789.
never read the whole file as it is quite long and will incur high costs to read all.

the logic is to seperated code into blocks which define the wrapping and indentation.
debug the code, plan and implement changes in GtsSupport.kt to fix the test.
you are ALSO allowed to do changes to GtsFormatIdentTest.kt.

The test works if manually run in IDE, but not in test suit.