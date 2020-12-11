package br.dev.pedrolamarao.java.nio.windows;

interface WindowsChannel
{
	void complete (long operation, boolean status, int result);
}
