package app.cash.quickjs

class QuickJsException : RuntimeException {
	constructor(message: String) : super(message)
	constructor(message: String, jsStack: String) : super("$message\n$jsStack")
}
