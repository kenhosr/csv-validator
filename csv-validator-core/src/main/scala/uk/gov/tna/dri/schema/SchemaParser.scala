package uk.gov.tna.dri.schema

import util.parsing.combinator._
import java.io.Reader
import util.Try

trait SchemaParser extends RegexParsers {

  override protected val whiteSpace = """[ \t]*""".r

  val white: Parser[String] = whiteSpace

  val eol = sys.props("line.separator")

  val columnIdentifier: Parser[String] = ("""\w+\b"""r) withFailureMessage("Column identifier invalid")

  val positiveNumber: Parser[String] = """[1-9][0-9]*"""r

  val Regex = """([(]")(.*)("[)])"""r

  val regexParser: Parser[String] = Regex withFailureMessage("""regex not correctly delimited as ("your regex")""")

  def parse(reader: Reader) = parseAll(schema, reader)

  def schema = totalColumns ~ columnDefinitions ^? (createSchema, { case t ~ c => s"Schema invalid as @TotalColumns = ${t} but number of columns defined = ${c.length}" })

  def totalColumns = (("@TotalColumns" ~ white) ~> positiveNumber <~ eol ^^ { _.toInt }).withFailureMessage("@TotalColumns invalid")

  def columnDefinitions = rep1(columnDefinition)

  def columnDefinition = (columnIdentifier <~ ":") ~ opt(regex) ~ opt(inRule) ~ opt(optional) <~ endOfColumnDefinition ^^ {
    case id ~ reg ~ in ~ o => ColumnDefinition(id, List(reg, in).collect { case Some(r) => r }, List(o).collect { case Some(o) => o } )
  }

  def regex = ("regex" ~ white) ~> regexParser ^? (validateRegex, s => "regex invalid: " + stripRegexDelimiters(s)) | failure("Invalid regex rule")

  def inRule = "in(" ~> stringProvider <~ ")"  ^^ {InRule(_)}

  def stringProvider: Parser[StringProvider] = """^\$\w+""".r ^^ {ColumnTypeProvider(_)} | "\\w*".r ^^ {LiteralTypeProvider(_)}

  def columnRef: Parser[StringProvider] = "$" ~> columnIdentifier ^^ {ColumnTypeProvider(_)}

  def optional = "@Optional" ^^^ Optional()

  private def createSchema: PartialFunction[~[Int, List[ColumnDefinition]], Schema] = {
    case totalColumns ~ columnDefinitions if totalColumns == columnDefinitions.length => Schema(totalColumns, columnDefinitions)
  }

  private def endOfColumnDefinition: Parser[Any] = whiteSpace ~ (eol | endOfInput | failure("Column definition contains invalid (extra) text"))

  private def endOfInput: Parser[Any] = new Parser[Any] {
    def apply(input: Input) = {
      if (input.atEnd) new Success("End of Input reached", input)
      else Failure("End of Input expected", input)
    }
  }

  private def validateRegex: PartialFunction[String, RegexRule] = {
    case Regex(_, s, _) if Try(s.r).isSuccess => RegexRule(Try(s.r).get)
  }

  private def stripRegexDelimiters(s: String) = s.drop(2).dropRight(2)
}